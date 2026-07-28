import json
import math
import re
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from flask import Blueprint, jsonify, request

import platform_miniapp
from platform_identity import extract_valid_vin
from platform_license import parse_utc
from platform_storage import db, utc_now


CHINA_TIMEZONE = ZoneInfo("Asia/Shanghai")


def numeric_version_parts(value):
    normalized = str(value or "").strip().lower().removeprefix("v")
    if not normalized:
        return None
    parts = re.findall(r"\d+", normalized)
    if not parts:
        return None
    return tuple(int(part) for part in parts)


def version_is_newer(current, latest):
    current_parts = numeric_version_parts(current)
    latest_parts = numeric_version_parts(latest)
    if current_parts is None or latest_parts is None:
        return False
    width = max(len(current_parts), len(latest_parts))
    current_parts += (0,) * (width - len(current_parts))
    latest_parts += (0,) * (width - len(latest_parts))
    return latest_parts > current_parts


def create_miniapp_blueprint(
    *,
    device_token_matches,
    device_authorization,
    authorization_category,
    vehicle_presence,
    vehicle_display_label,
    latest_release,
    api_lock_remaining,
    record_api_success,
    record_api_failure,
    client_key,
):
    blueprint = Blueprint("miniapp_api", __name__)

    def bearer_token():
        authorization = request.headers.get("Authorization", "").strip()
        scheme, separator, token = authorization.partition(" ")
        if not separator or scheme.lower() != "bearer":
            return ""
        return token.strip()[:256]

    def miniapp_user(conn):
        return platform_miniapp.authenticated_user(
            conn,
            bearer_token(),
            request.remote_addr or "",
        )

    def unauthorized():
        return jsonify({"success": False, "message": "登录状态已失效，请重新进入小程序"}), 401

    def bound_vehicle_or_response(conn, user):
        device = platform_miniapp.bound_device(conn, user["id"])
        if device is None:
            return None, None
        return device, None

    def expire_remote_commands(conn):
        now = utc_now()
        conn.execute(
            """
            UPDATE miniapp_remote_commands
            SET status = 'expired', message = '车机未在有效时间内处理', completed_at = ?
            WHERE status IN ('pending', 'delivered') AND expires_at <= ?
            """,
            (now, now),
        )

    def remote_command_payload(row):
        if row is None:
            return None
        try:
            payload = json.loads(row["payload_json"] or "{}")
        except (TypeError, ValueError):
            payload = {}
        try:
            result = json.loads(row["result_json"] or "{}")
        except (TypeError, ValueError):
            result = {}
        return {
            "id": row["id"],
            "type": row["command_type"],
            "payload": payload,
            "status": row["status"],
            "message": row["message"],
            "requestedAt": row["requested_at"],
            "deliveredAt": row["delivered_at"],
            "completedAt": row["completed_at"],
            "expiresAt": row["expires_at"],
            "result": result,
        }

    def local_sound_payload(row):
        return {
            "id": row["sound_key"],
            "soundKey": row["sound_key"],
            "name": row["display_name"],
            "fileName": row["file_name"],
            "fileSize": row["file_size"],
            "modifiedAt": row["modified_at"],
            "syncedAt": row["synced_at"],
        }

    def sync_local_sounds(conn, device_id, raw_items):
        if not isinstance(raw_items, list):
            return False
        normalized = []
        seen = set()
        for item in raw_items[:200]:
            if not isinstance(item, dict):
                continue
            sound_key = str(item.get("soundKey", "")).strip().lower()
            file_name = str(item.get("fileName", "")).strip()[:180]
            display_name = str(item.get("displayName", "")).strip()[:160]
            if not re.fullmatch(r"[a-f0-9]{64}", sound_key) or sound_key in seen:
                continue
            if not file_name or file_name != file_name.rsplit("/", 1)[-1] or "\\" in file_name:
                continue
            suffix = file_name.rsplit(".", 1)[-1].lower() if "." in file_name else ""
            if suffix not in {"mp3", "wav", "aac", "m4a", "ogg", "flac"}:
                continue
            try:
                file_size = max(0, min(int(item.get("fileSize", 0)), 1024 * 1024 * 1024))
                modified_at = max(0, int(item.get("modifiedAt", 0)))
            except (TypeError, ValueError):
                continue
            normalized.append((sound_key, file_name, display_name or file_name.rsplit(".", 1)[0], file_size, modified_at))
            seen.add(sound_key)

        now = utc_now()
        conn.execute("DELETE FROM vehicle_local_sounds WHERE device_id = ?", (device_id,))
        conn.executemany(
            """
            INSERT INTO vehicle_local_sounds
                (device_id, sound_key, file_name, display_name, file_size, modified_at, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            [(device_id, *item, now) for item in normalized],
        )
        return True

    def command_expiry():
        return (datetime.utcnow() + timedelta(minutes=10)).replace(microsecond=0).isoformat() + "Z"

    def request_device(conn):
        payload = request.get_json(silent=True) or {}
        vin = extract_valid_vin(payload.get("deviceCode", ""))
        if not vin:
            return None, (jsonify({"success": False, "message": "车架号格式不正确"}), 400)
        device = conn.execute("SELECT * FROM devices WHERE device_code = ?", (vin,)).fetchone()
        if device is None:
            return None, (jsonify({"success": False, "message": "车辆尚未在平台登记"}), 404)
        if not device_token_matches(device, bearer_token()):
            return None, (jsonify({"success": False, "message": "设备凭证无效，请刷新授权状态"}), 401)
        return device, None

    def relative_sync_label(value):
        parsed = parse_utc(value)
        if parsed is None:
            return "从未同步"
        age_seconds = max(0, int((datetime.utcnow() - parsed).total_seconds()))
        if age_seconds < 90:
            return "刚刚同步"
        if age_seconds < 3600:
            return f"{max(1, age_seconds // 60)}分钟前同步"
        local = parsed.replace(tzinfo=ZoneInfo("UTC")).astimezone(CHINA_TIMEZONE)
        now_local = datetime.now(CHINA_TIMEZONE)
        if local.date() == now_local.date():
            return local.strftime("今天 %H:%M 同步")
        return local.strftime("%m-%d %H:%M 同步")

    def authorization_payload(conn, device):
        authorization = device_authorization(conn, device)
        category = authorization_category(authorization)
        if authorization["activated"]:
            expires_at = "永久"
        elif device["trial_ends_at"]:
            expires_at = str(device["trial_ends_at"])[:10]
        else:
            expires_at = "已到期"
        return {
            "status": category[0],
            "label": category[1],
            "expiresAt": expires_at,
        }

    def vehicle_payload(device):
        presence = vehicle_presence(device)
        state = "sleeping" if presence[0] == "sleep" else presence[0]
        electric_range = device["electric_range_km"]
        fuel_range = device["fuel_range_km"]
        ranges = [value for value in (electric_range, fuel_range) if value is not None]
        range_km = sum(ranges) if ranges else None
        gear = device["gear"]
        gear_label = {1: "P 挡", 2: "R 挡", 3: "N 挡", 4: "D 挡"}.get(gear, "--")
        lock_label = {1: "未锁车", 2: "已锁车"}.get(device["lock_state"], "--")
        if state == "offline":
            runtime_label = "车辆离线"
        elif state == "sleeping":
            runtime_label = "车辆休眠"
        elif device["charging_status"] == 1:
            runtime_label = "充电中"
        elif gear == 4:
            runtime_label = "行驶中"
        else:
            runtime_label = "驻车中"
        return {
            "displayName": vehicle_display_label(device) or "未识别车型",
            "vinMasked": platform_miniapp.mask_vin(device["device_code"]),
            "onlineState": state,
            "onlineLabel": f"车辆{presence[1]}",
            "updatedLabel": relative_sync_label(device["telemetry_updated_at"] or device["last_seen_at"]),
            "soc": device["soc"],
            "rangeKm": range_km,
            "gearLabel": gear_label,
            "lockLabel": lock_label,
            "runtimeLabel": runtime_label,
            "insideTemp": device["inside_temp"],
            "outsideTemp": device["outside_temp"],
            "mileageKm": device["mileage_km"],
            "voltage12v": device["voltage_12v"],
            "connectionLabel": presence[1],
        }

    def month_window_ms():
        now = datetime.now(CHINA_TIMEZONE)
        start = datetime(now.year, now.month, 1, tzinfo=CHINA_TIMEZONE)
        if now.month == 12:
            end = datetime(now.year + 1, 1, 1, tzinfo=CHINA_TIMEZONE)
        else:
            end = datetime(now.year, now.month + 1, 1, tzinfo=CHINA_TIMEZONE)
        return int(start.timestamp() * 1000), int(end.timestamp() * 1000), now

    def day_window_ms():
        now = datetime.now(CHINA_TIMEZONE)
        start = datetime(now.year, now.month, now.day, tzinfo=CHINA_TIMEZONE)
        return int(start.timestamp() * 1000), int((start + timedelta(days=1)).timestamp() * 1000)

    def local_time(timestamp_ms):
        return datetime.fromtimestamp(int(timestamp_ms) / 1000, tz=CHINA_TIMEZONE)

    def trip_duration_minutes(row):
        end_ts = row["end_ts"] or row["start_ts"]
        return max(0, int(round((end_ts - row["start_ts"]) / 60000)))

    def trip_consumption(row):
        if row["kwh_per_100km"] is not None:
            return round(row["kwh_per_100km"], 1)
        if row["kwh_consumed"] is not None and row["distance_km"] and row["distance_km"] > 0:
            return round(row["kwh_consumed"] * 100 / row["distance_km"], 1)
        return None

    def trip_item(row, color="blue"):
        started = local_time(row["start_ts"])
        return {
            "id": str(row["id"]),
            "day": started.strftime("%d"),
            "month": started.strftime("%m月"),
            "startName": row["start_name"] or "行程起点",
            "endName": row["end_name"] or "行程终点",
            "distanceKm": round(row["distance_km"] or 0, 1),
            "durationMinutes": trip_duration_minutes(row),
            "time": started.strftime("%H:%M"),
            "consumption": trip_consumption(row),
            "color": color,
        }

    def route_payload(conn, device_id, trip_source_id):
        points = conn.execute(
            """
            SELECT * FROM vehicle_trip_points
            WHERE device_id = ? AND trip_source_id = ?
            ORDER BY timestamp ASC LIMIT 1200
            """,
            (device_id, str(trip_source_id)),
        ).fetchall()
        if not points:
            return None
        coordinates = [
            {"latitude": point["lat"], "longitude": point["lon"]}
            for point in points
        ]
        first = coordinates[0]
        last = coordinates[-1]
        return {
            "center": {
                "latitude": sum(point["latitude"] for point in coordinates) / len(coordinates),
                "longitude": sum(point["longitude"] for point in coordinates) / len(coordinates),
            },
            "markers": [
                {
                    "id": 1,
                    "latitude": first["latitude"],
                    "longitude": first["longitude"],
                    "title": "起点",
                    "iconPath": "/assets/map/start.png",
                    "width": 28,
                    "height": 28,
                },
                {
                    "id": 2,
                    "latitude": last["latitude"],
                    "longitude": last["longitude"],
                    "title": "终点",
                    "iconPath": "/assets/map/end.png",
                    "width": 28,
                    "height": 28,
                },
            ],
            "polyline": [
                {
                    "color": "#1769F5",
                    "width": 6,
                    "arrowLine": True,
                    "borderColor": "#FFFFFF",
                    "borderWidth": 2,
                    "points": coordinates,
                }
            ],
        }

    def optional_int(value, minimum=None, maximum=None):
        if value is None or isinstance(value, bool):
            return None
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return None
        if minimum is not None and parsed < minimum:
            return None
        if maximum is not None and parsed > maximum:
            return None
        return parsed

    def optional_float(value, minimum=None, maximum=None):
        if value is None or isinstance(value, bool):
            return None
        try:
            parsed = float(value)
        except (TypeError, ValueError):
            return None
        if not math.isfinite(parsed):
            return None
        if minimum is not None and parsed < minimum:
            return None
        if maximum is not None and parsed > maximum:
            return None
        return parsed

    def source_id(value):
        raw = str(value or "").strip()
        return raw[:80] if raw else ""

    def timestamp_ms(value):
        parsed = optional_int(value, 946684800000, int((datetime.utcnow() + timedelta(days=2)).timestamp() * 1000))
        return parsed

    @blueprint.post("/api/v1/miniapp/login")
    def login():
        payload = request.get_json(silent=True) or {}
        with db() as conn:
            attempt_key = f"miniapp_login:{client_key()}"
            remaining = api_lock_remaining(conn, attempt_key)
            if remaining > 0:
                return jsonify({"success": False, "message": f"请求过于频繁，请 {remaining} 秒后再试"}), 429
            try:
                identity = platform_miniapp.exchange_wechat_code(payload.get("code"))
            except platform_miniapp.MiniappConfigurationError as error:
                return jsonify({"success": False, "message": str(error)}), 503
            except platform_miniapp.MiniappLoginError as error:
                record_api_failure(conn, attempt_key, max_failures=10, lock_seconds=300)
                return jsonify({"success": False, "message": str(error)}), 401
            user = platform_miniapp.upsert_wechat_user(conn, identity)
            session_payload = platform_miniapp.issue_session(conn, user["id"], request.remote_addr or "")
            record_api_success(conn, attempt_key)
        return jsonify(session_payload)

    @blueprint.post("/api/v1/miniapp/vehicles/binding-code")
    def generate_binding_code():
        with db() as conn:
            device, error_response = request_device(conn)
            if error_response:
                return error_response
            result = platform_miniapp.create_binding_code(conn, device["id"], request.remote_addr or "")
        return jsonify({"success": True, **result})

    @blueprint.post("/api/v1/miniapp/vehicles/bind")
    def bind_vehicle():
        payload = request.get_json(silent=True) or {}
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            attempt_key = f"miniapp_bind:{user['id']}:{client_key()}"
            remaining = api_lock_remaining(conn, attempt_key)
            if remaining > 0:
                return jsonify({"success": False, "message": f"尝试次数过多，请 {remaining} 秒后再试"}), 429
            device = platform_miniapp.consume_binding_code(conn, user["id"], payload.get("bindingCode"))
            if device is None:
                remaining = record_api_failure(conn, attempt_key)
                message = f"尝试次数过多，请 {remaining} 秒后再试" if remaining > 0 else "绑定码无效或已过期"
                return jsonify({"success": False, "message": message}), 429 if remaining > 0 else 400
            record_api_success(conn, attempt_key)
        return jsonify(
            {
                "success": True,
                "vehicle": {
                    "displayName": vehicle_display_label(device) or "未识别车型",
                    "vinMasked": platform_miniapp.mask_vin(device["device_code"]),
                },
            }
        )

    @blueprint.get("/api/v1/miniapp/dashboard")
    def dashboard():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"vehicle": None, "today": {"distanceKm": 0, "durationMinutes": 0, "avgConsumption": None, "tripCount": 0}})
            start_ms, end_ms = day_window_ms()
            summary = conn.execute(
                """
                SELECT COALESCE(SUM(distance_km), 0) AS distance_km,
                       COALESCE(SUM(CASE WHEN end_ts IS NOT NULL THEN end_ts - start_ts ELSE 0 END), 0) AS duration_ms,
                       COALESCE(SUM(kwh_consumed), 0) AS total_kwh,
                       COUNT(*) AS trip_count
                FROM vehicle_trips
                WHERE device_id = ? AND start_ts >= ? AND start_ts < ?
                """,
                (device["id"], start_ms, end_ms),
            ).fetchone()
            distance = float(summary["distance_km"] or 0)
            consumption = (float(summary["total_kwh"] or 0) * 100 / distance) if distance > 0 else None
            today = {
                "distanceKm": round(distance, 1),
                "durationMinutes": int(round((summary["duration_ms"] or 0) / 60000)),
                "avgConsumption": round(consumption, 1) if consumption is not None else None,
                "tripCount": summary["trip_count"],
            }
        return jsonify({"vehicle": vehicle_payload(device), "today": today})

    @blueprint.get("/api/v1/miniapp/trips")
    def trips():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"month": "", "summary": {"distanceKm": 0, "tripCount": 0, "avgConsumption": None}, "latestRoute": None, "items": [], "charges": []})
            start_ms, end_ms, now = month_window_ms()
            rows = conn.execute(
                """
                SELECT * FROM vehicle_trips
                WHERE device_id = ? AND start_ts >= ? AND start_ts < ?
                ORDER BY start_ts DESC LIMIT 100
                """,
                (device["id"], start_ms, end_ms),
            ).fetchall()
            charges = conn.execute(
                """
                SELECT * FROM vehicle_charges
                WHERE device_id = ? AND start_ts >= ? AND start_ts < ?
                ORDER BY start_ts DESC LIMIT 100
                """,
                (device["id"], start_ms, end_ms),
            ).fetchall()
            distance = sum(float(row["distance_km"] or 0) for row in rows)
            total_kwh = sum(float(row["kwh_consumed"] or 0) for row in rows)
            latest_route = route_payload(conn, device["id"], rows[0]["source_id"]) if rows else None
            colors = ("blue", "green", "yellow", "coral")
            trip_items = [trip_item(row, colors[index % len(colors)]) for index, row in enumerate(rows)]
            charge_items = []
            for row in charges:
                started = local_time(row["start_ts"])
                duration = max(0, int(round(((row["end_ts"] or row["start_ts"]) - row["start_ts"]) / 60000)))
                charge_items.append(
                    {
                        "id": str(row["id"]),
                        "day": started.strftime("%d"),
                        "month": started.strftime("%m月"),
                        "type": "直流快充" if row["charge_type"] == "DC" else "交流慢充" if row["charge_type"] == "AC" else "充电记录",
                        "energyKwh": round(row["kwh_charged"] or 0, 1),
                        "durationMinutes": duration,
                        "socText": f"{row['soc_start']}% → {row['soc_end']}%" if row["soc_start"] is not None and row["soc_end"] is not None else "电量未记录",
                        "cost": f"{row['cost']:.2f}" if row["cost"] is not None else "--",
                    }
                )
            average = total_kwh * 100 / distance if distance > 0 else None
            result = {
                "month": f"{now.year}年{now.month}月",
                "summary": {
                    "distanceKm": round(distance, 1),
                    "tripCount": len(rows),
                    "avgConsumption": round(average, 1) if average is not None else None,
                },
                "latestRoute": latest_route,
                "items": trip_items,
                "charges": charge_items,
            }
        return jsonify(result)

    @blueprint.get("/api/v1/miniapp/trips/<int:trip_id>")
    def trip_detail(trip_id):
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"success": False, "message": "尚未绑定车辆"}), 404
            row = conn.execute(
                "SELECT * FROM vehicle_trips WHERE id = ? AND device_id = ?",
                (trip_id, device["id"]),
            ).fetchone()
            if row is None:
                return jsonify({"success": False, "message": "行程不存在"}), 404
            result = trip_item(row)
            result["route"] = route_payload(conn, device["id"], row["source_id"])
        return jsonify(result)

    @blueprint.get("/api/v1/miniapp/tools")
    def tools():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                sync = {"onlineState": "offline", "statusLabel": "未绑定", "description": "绑定车辆后同步车机配置与记录"}
            else:
                presence = vehicle_presence(device)
                state = "sleeping" if presence[0] == "sleep" else presence[0]
                sync = {
                    "onlineState": state,
                    "statusLabel": presence[1],
                    "description": "车辆在线，配置与记录已同步" if state == "online" else f"车辆{presence[1]}，显示最后一次同步数据",
                }
        items = [
            {"id": "sounds", "title": "远程播放音效", "subtitle": "播放车机 WJX 文件夹中的音效", "icon": "volume-2", "tone": "coral", "route": "/pages/web-content/index?type=sounds"},
            {"id": "automation", "title": "自动化", "subtitle": "查看规则与最近执行记录", "icon": "workflow", "tone": "green", "route": "/pages/web-content/index?type=automation"},
            {"id": "diagnostics", "title": "车机诊断", "subtitle": "上传日志并查看处理状态", "icon": "file-up", "tone": "blue", "route": "/pages/web-content/index?type=diagnostics"},
            {"id": "updates", "title": "版本更新", "subtitle": "查看新版功能与安装状态", "icon": "download", "tone": "yellow", "route": "/pages/web-content/index?type=updates"},
            {"id": "guide", "title": "使用说明", "subtitle": "完整功能说明和操作步骤", "icon": "book-open", "tone": "cyan", "route": "/pages/web-content/index?type=guide"},
            {"id": "service", "title": "联系客服", "subtitle": "授权、安装和问题咨询", "icon": "headphones", "tone": "violet", "route": "/pages/web-content/index?type=service"},
        ]
        return jsonify({"sync": sync, "items": items})

    @blueprint.get("/api/v1/miniapp/remote-audio")
    def remote_audio():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"success": False, "message": "尚未绑定车辆"}), 400
            expire_remote_commands(conn)
            authorization = device_authorization(conn, device)
            presence = vehicle_presence(device)
            state = "sleeping" if presence[0] == "sleep" else presence[0]
            sounds = conn.execute(
                """
                SELECT * FROM vehicle_local_sounds
                WHERE device_id = ?
                ORDER BY display_name COLLATE NOCASE, file_name COLLATE NOCASE LIMIT 200
                """,
                (device["id"],),
            ).fetchall()
            active = conn.execute(
                """
                SELECT * FROM miniapp_remote_commands
                WHERE device_id = ? AND requested_by_user_id = ?
                  AND status IN ('pending', 'delivered')
                ORDER BY requested_at DESC LIMIT 1
                """,
                (device["id"], user["id"]),
            ).fetchone()
            latest = active or conn.execute(
                """
                SELECT * FROM miniapp_remote_commands
                WHERE device_id = ? AND requested_by_user_id = ?
                ORDER BY requested_at DESC LIMIT 1
                """,
                (device["id"], user["id"]),
            ).fetchone()
            return jsonify(
                {
                    "success": True,
                    "vehicle": {
                        "displayName": vehicle_display_label(device) or "未识别车型",
                        "onlineState": state,
                        "onlineLabel": presence[1],
                        "updatedLabel": relative_sync_label(device["last_seen_at"]),
                    },
                    "authorization": authorization_payload(conn, device),
                    "canControl": bool(authorization["activated"] or authorization["trial_active"]),
                    "sounds": [local_sound_payload(row) for row in sounds],
                    "command": remote_command_payload(latest),
                }
            )

    @blueprint.post("/api/v1/miniapp/remote-audio/play")
    def remote_audio_play():
        payload = request.get_json(silent=True) or {}
        sound_key = str(payload.get("soundKey", "")).strip().lower()
        if not re.fullmatch(r"[a-f0-9]{64}", sound_key):
            return jsonify({"success": False, "message": "请选择有效音效"}), 400
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"success": False, "message": "尚未绑定车辆"}), 400
            authorization = device_authorization(conn, device)
            if not authorization["activated"] and not authorization["trial_active"]:
                return jsonify({"success": False, "message": "车辆授权已到期，无法远程播放"}), 403
            sound = conn.execute(
                "SELECT * FROM vehicle_local_sounds WHERE device_id = ? AND sound_key = ?",
                (device["id"], sound_key),
            ).fetchone()
            if sound is None:
                return jsonify({"success": False, "message": "该音效已不在车机 WJX 文件夹，请刷新列表"}), 404
            expire_remote_commands(conn)
            active = conn.execute(
                """
                SELECT * FROM miniapp_remote_commands
                WHERE device_id = ? AND status IN ('pending', 'delivered')
                ORDER BY requested_at DESC LIMIT 1
                """,
                (device["id"],),
            ).fetchone()
            if active is not None:
                return jsonify(
                    {
                        "success": False,
                        "message": "上一条远程指令仍在处理中，请稍后重试",
                        "command": remote_command_payload(active),
                    }
                ), 409
            now = utc_now()
            command_payload = local_sound_payload(sound)
            cursor = conn.execute(
                """
                INSERT INTO miniapp_remote_commands
                    (device_id, requested_by_user_id, command_type, payload_json,
                     status, message, requested_at, expires_at)
                VALUES (?, ?, 'play_outside_sound', ?, 'pending', ?, ?, ?)
                """,
                (
                    device["id"],
                    user["id"],
                    json.dumps(command_payload, ensure_ascii=False, separators=(",", ":")),
                    "正在等待车机",
                    now,
                    command_expiry(),
                ),
            )
            row = conn.execute(
                "SELECT * FROM miniapp_remote_commands WHERE id = ?",
                (cursor.lastrowid,),
            ).fetchone()
        return jsonify({"success": True, "command": remote_command_payload(row)})

    @blueprint.post("/api/v1/miniapp/remote-audio/stop")
    def remote_audio_stop():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"success": False, "message": "尚未绑定车辆"}), 400
            now = utc_now()
            expire_remote_commands(conn)
            existing_stop = conn.execute(
                """
                SELECT * FROM miniapp_remote_commands
                WHERE device_id = ? AND status IN ('pending', 'delivered')
                  AND command_type = 'stop_outside_sound'
                ORDER BY requested_at DESC LIMIT 1
                """,
                (device["id"],),
            ).fetchone()
            if existing_stop is not None:
                return jsonify({"success": True, "command": remote_command_payload(existing_stop)})
            conn.execute(
                """
                UPDATE miniapp_remote_commands
                SET status = 'cancelled', message = '已被停止指令取代', completed_at = ?
                WHERE device_id = ? AND status IN ('pending', 'delivered')
                  AND command_type = 'play_outside_sound'
                """,
                (now, device["id"]),
            )
            cursor = conn.execute(
                """
                INSERT INTO miniapp_remote_commands
                    (device_id, requested_by_user_id, command_type, payload_json,
                     status, message, requested_at, expires_at)
                VALUES (?, ?, 'stop_outside_sound', '{}', 'pending', ?, ?, ?)
                """,
                (device["id"], user["id"], "正在等待车机停止播放", now, command_expiry()),
            )
            row = conn.execute(
                "SELECT * FROM miniapp_remote_commands WHERE id = ?",
                (cursor.lastrowid,),
            ).fetchone()
        return jsonify({"success": True, "command": remote_command_payload(row)})

    @blueprint.get("/api/v1/miniapp/remote-audio/commands/<int:command_id>")
    def remote_audio_command(command_id):
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"success": False, "message": "尚未绑定车辆"}), 400
            expire_remote_commands(conn)
            row = conn.execute(
                """
                SELECT * FROM miniapp_remote_commands
                WHERE id = ? AND device_id = ? AND requested_by_user_id = ?
                """,
                (command_id, device["id"], user["id"]),
            ).fetchone()
            if row is None:
                return jsonify({"success": False, "message": "远程指令不存在"}), 404
        return jsonify({"success": True, "command": remote_command_payload(row)})

    @blueprint.post("/api/v1/vehicles/commands/poll")
    def vehicle_command_poll():
        payload = request.get_json(silent=True) or {}
        with db() as conn:
            device, error_response = request_device(conn)
            if error_response:
                return error_response
            now = utc_now()
            expire_remote_commands(conn)
            if "localSounds" in payload:
                sync_local_sounds(conn, device["id"], payload.get("localSounds"))
            conn.execute(
                "UPDATE devices SET last_seen_at = ?, remote_addr = ? WHERE id = ?",
                (now, request.remote_addr or "", device["id"]),
            )
            row = conn.execute(
                """
                SELECT * FROM miniapp_remote_commands
                WHERE device_id = ? AND status IN ('pending', 'delivered')
                ORDER BY CASE WHEN status = 'pending' THEN 0 ELSE 1 END,
                         requested_at ASC LIMIT 1
                """,
                (device["id"],),
            ).fetchone()
            if row is not None and row["status"] == "pending":
                conn.execute(
                    """
                    UPDATE miniapp_remote_commands
                    SET status = 'delivered', message = '车机已接收', delivered_at = ?
                    WHERE id = ? AND status = 'pending'
                    """,
                    (now, row["id"]),
                )
                row = conn.execute(
                    "SELECT * FROM miniapp_remote_commands WHERE id = ?",
                    (row["id"],),
                ).fetchone()
        return jsonify({"success": True, "command": remote_command_payload(row)})

    @blueprint.post("/api/v1/vehicles/commands/<int:command_id>/complete")
    def vehicle_command_complete(command_id):
        payload = request.get_json(silent=True) or {}
        reported_status = str(payload.get("status", "")).strip().lower()
        if reported_status not in {"delivered", "completed", "failed"}:
            return jsonify({"success": False, "message": "指令状态无效"}), 400
        message = str(payload.get("message", "")).strip()[:500]
        result = payload.get("result") if isinstance(payload.get("result"), dict) else {}
        with db() as conn:
            device, error_response = request_device(conn)
            if error_response:
                return error_response
            row = conn.execute(
                "SELECT * FROM miniapp_remote_commands WHERE id = ? AND device_id = ?",
                (command_id, device["id"]),
            ).fetchone()
            if row is None:
                return jsonify({"success": False, "message": "远程指令不存在"}), 404
            if row["status"] in {"completed", "failed", "cancelled", "expired"}:
                return jsonify({"success": True, "command": remote_command_payload(row)})
            now = utc_now()
            completed_at = now if reported_status in {"completed", "failed"} else None
            conn.execute(
                """
                UPDATE miniapp_remote_commands
                SET status = ?, message = ?, result_json = ?,
                    delivered_at = COALESCE(delivered_at, ?), completed_at = ?
                WHERE id = ?
                """,
                (
                    reported_status,
                    message or ("车机已接收" if reported_status == "delivered" else "执行完成"),
                    json.dumps(result, ensure_ascii=False, separators=(",", ":")),
                    now,
                    completed_at,
                    command_id,
                ),
            )
            updated = conn.execute(
                "SELECT * FROM miniapp_remote_commands WHERE id = ?",
                (command_id,),
            ).fetchone()
        return jsonify({"success": True, "command": remote_command_payload(updated)})

    @blueprint.get("/api/v1/miniapp/profile")
    def profile():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            release = latest_release()
            if device is None:
                return jsonify(
                    {
                        "loggedIn": True,
                        "vehicle": None,
                        "authorization": {"status": "expired", "label": "未绑定", "expiresAt": "--"},
                        "diagnostic": {"lastUploadLabel": "暂无上传记录", "lastUploadAt": "--"},
                        "version": {"current": "--", "latest": release["version"] if release else "--", "updateAvailable": False},
                    }
                )
            latest_log = conn.execute(
                "SELECT * FROM diagnostic_logs WHERE device_code = ? ORDER BY created_at DESC LIMIT 1",
                (device["device_code"],),
            ).fetchone()
            current_version = str(device["app_version"] or "--")
            latest_version = str(release["version"] if release else current_version)
            result = {
                "loggedIn": True,
                "vehicle": {
                    "displayName": vehicle_display_label(device) or "未识别车型",
                    "vinMasked": platform_miniapp.mask_vin(device["device_code"]),
                },
                "authorization": authorization_payload(conn, device),
                "diagnostic": {
                    "lastUploadLabel": "最近上传成功" if latest_log else "暂无上传记录",
                    "lastUploadAt": relative_sync_label(latest_log["created_at"]) if latest_log else "--",
                },
                "version": {
                    "current": current_version if current_version.startswith("v") else f"v{current_version}" if current_version != "--" else current_version,
                    "latest": latest_version if latest_version.startswith("v") else f"v{latest_version}" if latest_version != "--" else latest_version,
                    "updateAvailable": bool(release and version_is_newer(current_version, latest_version)),
                },
            }
        return jsonify(result)

    @blueprint.post("/api/v1/miniapp/logs/request-upload")
    def request_log_upload():
        with db() as conn:
            user = miniapp_user(conn)
            if user is None:
                return unauthorized()
            device, _ = bound_vehicle_or_response(conn, user)
            if device is None:
                return jsonify({"success": False, "message": "尚未绑定车辆"}), 400
            existing = conn.execute(
                """
                SELECT * FROM miniapp_log_requests
                WHERE device_id = ? AND status IN ('pending', 'acknowledged')
                ORDER BY requested_at DESC LIMIT 1
                """,
                (device["id"],),
            ).fetchone()
            if existing is not None:
                return jsonify({"success": True, "requestId": existing["id"], "message": "日志上传请求已在等待车机处理"})
            cursor = conn.execute(
                """
                INSERT INTO miniapp_log_requests
                    (device_id, requested_by_user_id, status, message, requested_at)
                VALUES (?, ?, 'pending', ?, ?)
                """,
                (device["id"], user["id"], "等待车机上线", utc_now()),
            )
        return jsonify({"success": True, "requestId": cursor.lastrowid, "message": "日志上传请求已发送，等待车机回执"})

    @blueprint.post("/api/v1/vehicles/history-sync")
    def history_sync():
        payload = request.get_json(silent=True) or {}
        with db() as conn:
            device, error_response = request_device(conn)
            if error_response:
                return error_response
            trips_payload = payload.get("trips") if isinstance(payload.get("trips"), list) else []
            points_payload = payload.get("tripPoints") if isinstance(payload.get("tripPoints"), list) else []
            charges_payload = payload.get("charges") if isinstance(payload.get("charges"), list) else []
            if len(trips_payload) > 250 or len(points_payload) > 2500 or len(charges_payload) > 250:
                return jsonify({"success": False, "message": "单次同步数据过多，请分批上传"}), 413
            now = utc_now()
            trip_count = 0
            for item in trips_payload:
                if not isinstance(item, dict):
                    continue
                sid = source_id(item.get("sourceId"))
                start = timestamp_ms(item.get("startTs"))
                if not sid or start is None:
                    continue
                conn.execute(
                    """
                    INSERT INTO vehicle_trips
                        (device_id, source_id, start_ts, end_ts, distance_km, kwh_consumed,
                         kwh_per_100km, avg_speed_kmh, soc_start, soc_end, cost,
                         start_name, end_name, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(device_id, source_id) DO UPDATE SET
                        start_ts = excluded.start_ts,
                        end_ts = excluded.end_ts,
                        distance_km = excluded.distance_km,
                        kwh_consumed = excluded.kwh_consumed,
                        kwh_per_100km = excluded.kwh_per_100km,
                        avg_speed_kmh = excluded.avg_speed_kmh,
                        soc_start = excluded.soc_start,
                        soc_end = excluded.soc_end,
                        cost = excluded.cost,
                        start_name = COALESCE(NULLIF(excluded.start_name, ''), vehicle_trips.start_name),
                        end_name = COALESCE(NULLIF(excluded.end_name, ''), vehicle_trips.end_name),
                        updated_at = excluded.updated_at
                    """,
                    (
                        device["id"], sid, start, timestamp_ms(item.get("endTs")),
                        optional_float(item.get("distanceKm"), 0, 5000),
                        optional_float(item.get("kwhConsumed"), 0, 2000),
                        optional_float(item.get("kwhPer100km"), 0, 200),
                        optional_float(item.get("avgSpeedKmh"), 0, 300),
                        optional_int(item.get("socStart"), 0, 100),
                        optional_int(item.get("socEnd"), 0, 100),
                        optional_float(item.get("cost"), 0, 100000),
                        str(item.get("startName") or "").strip()[:160],
                        str(item.get("endName") or "").strip()[:160],
                        now,
                    ),
                )
                trip_count += 1
            point_count = 0
            for item in points_payload:
                if not isinstance(item, dict):
                    continue
                sid = source_id(item.get("sourceId"))
                trip_sid = source_id(item.get("tripSourceId"))
                timestamp = timestamp_ms(item.get("timestamp"))
                lat = optional_float(item.get("lat"), -90, 90)
                lon = optional_float(item.get("lon"), -180, 180)
                if not sid or not trip_sid or timestamp is None or lat is None or lon is None:
                    continue
                conn.execute(
                    """
                    INSERT INTO vehicle_trip_points
                        (device_id, trip_source_id, source_id, timestamp, lat, lon, speed_kmh, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(device_id, source_id) DO UPDATE SET
                        trip_source_id = excluded.trip_source_id,
                        timestamp = excluded.timestamp,
                        lat = excluded.lat,
                        lon = excluded.lon,
                        speed_kmh = excluded.speed_kmh,
                        updated_at = excluded.updated_at
                    """,
                    (device["id"], trip_sid, sid, timestamp, lat, lon, optional_float(item.get("speedKmh"), 0, 350), now),
                )
                point_count += 1
            charge_count = 0
            for item in charges_payload:
                if not isinstance(item, dict):
                    continue
                sid = source_id(item.get("sourceId"))
                start = timestamp_ms(item.get("startTs"))
                if not sid or start is None:
                    continue
                charge_type = str(item.get("type") or "").strip().upper()[:16]
                conn.execute(
                    """
                    INSERT INTO vehicle_charges
                        (device_id, source_id, start_ts, end_ts, soc_start, soc_end,
                         kwh_charged, charge_type, cost, lat, lon, status, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(device_id, source_id) DO UPDATE SET
                        start_ts = excluded.start_ts,
                        end_ts = excluded.end_ts,
                        soc_start = excluded.soc_start,
                        soc_end = excluded.soc_end,
                        kwh_charged = excluded.kwh_charged,
                        charge_type = excluded.charge_type,
                        cost = excluded.cost,
                        lat = excluded.lat,
                        lon = excluded.lon,
                        status = excluded.status,
                        updated_at = excluded.updated_at
                    """,
                    (
                        device["id"], sid, start, timestamp_ms(item.get("endTs")),
                        optional_int(item.get("socStart"), 0, 100),
                        optional_int(item.get("socEnd"), 0, 100),
                        optional_float(item.get("kwhCharged"), 0, 2000),
                        charge_type,
                        optional_float(item.get("cost"), 0, 100000),
                        optional_float(item.get("lat"), -90, 90),
                        optional_float(item.get("lon"), -180, 180),
                        str(item.get("status") or "").strip()[:32],
                        now,
                    ),
                )
                charge_count += 1
        return jsonify({"success": True, "trips": trip_count, "tripPoints": point_count, "charges": charge_count})

    return blueprint

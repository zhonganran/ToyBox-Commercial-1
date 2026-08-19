package com.byd.wjx.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.96f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                ) {
                    Text(
                        text = "免责声明与使用须知",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "请在使用超级玩具箱前仔细阅读，滑动至底部后选择是否同意",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardSurface.copy(alpha = 0.78f))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DisclaimerItem(
                            title = "1. 软件性质",
                            body = "超级玩具箱为车机辅助工具，非车辆安全系统、非原车核心控制系统，也不属于比亚迪官方应用或官方授权服务。软件提供的功能仅用于提升车机使用体验，不构成任何维修、检测、改装、保险、理赔或法律场景的专业依据。",
                        )
                        DisclaimerItem(
                            title = "2. 安全驾驶",
                            body = "驾驶安全始终优先于软件功能。驾驶过程中请勿分心操作本软件，不得在复杂路况、行驶中或影响安全判断的场景下调试功能、切换音效、修改智能联动规则或操作悬浮窗。因驾驶中操作软件造成的风险和后果由用户自行承担。",
                        )
                        DisclaimerItem(
                            title = "3. 更新与兼容",
                            body = "软件后续可能根据功能、安全、稳定性或商业策略进行更新、调整、暂停或移除部分功能。更新过程中可能出现下载失败、安装失败或配置重置等情况。建议在安全停车状态下进行更新和设置操作。",
                        )
                        DisclaimerItem(
                            title = "4. 禁止行为",
                            body = "禁止将本软件用于违法违规用途；禁止破解、反编译、篡改、二次打包、绕过激活、传播盗版、恶意攻击服务器、批量刷取资源或干扰车机系统正常运行。因上述行为造成的全部责任由实施者自行承担。",
                        )
                        DisclaimerItem(
                            title = "5. 知识产权",
                            body = "本软件的界面设计、图标、代码、服务端接口、功能逻辑、文案及相关资源受法律保护。未经许可，不得用于复制、分发、转售、商业仿制或其他侵犯权益的行为。",
                        )
                        DisclaimerItem(
                            title = "6. 责任限制",
                            body = "在法律允许范围内，因安装、授权、设置、使用、无法使用本软件，或因车辆系统差异、网络异常、第三方内容、用户误操作等原因导致的直接或间接损失，开发者不承担超出法律强制规定范围外的责任。",
                        )
                        DisclaimerItem(
                            title = "7. 声明更新",
                            body = "本声明可能随软件版本、功能范围、法律法规或运营策略变化而更新。更新后的声明将在软件内展示，用户继续使用即视为已阅读并接受更新后的内容。若不同意，请停止使用并卸载本软件。",
                        )
                        DisclaimerItem(
                            title = "8. 法律适用",
                            body = "本声明的订立、执行和解释适用中华人民共和国法律。若部分条款被认定无效，不影响其他条款继续有效。用户点击同意即表示已充分阅读、理解并接受以上全部内容。",
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardSurfaceElevated.copy(alpha = 0.82f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentGreen,
                                uncheckedColor = TextMuted,
                                checkmarkColor = Color.White,
                            ),
                        )
                        Text(
                            text = "我已阅读并同意以上免责声明",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onDecline,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("不同意并退出")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onAccept,
                            enabled = checked,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.White,
                                disabledContainerColor = CardBorder,
                                disabledContentColor = TextMuted,
                            ),
                        ) {
                            Text("同意并进入", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}

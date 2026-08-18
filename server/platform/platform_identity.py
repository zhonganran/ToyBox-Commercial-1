def normalize_device_code(value):
    return "".join(character for character in (value or "").upper() if character.isalnum())


def extract_valid_vin(value):
    code = normalize_device_code(value)
    if (
        len(code) == 17
        and code.startswith("L")
        and code.isalnum()
        and not any(character in "IOQ" for character in code)
    ):
        return code
    return ""

from datetime import datetime


VIN_YEAR_CODES = {
    "A": 1980, "B": 1981, "C": 1982, "D": 1983, "E": 1984,
    "F": 1985, "G": 1986, "H": 1987, "J": 1988, "K": 1989,
    "L": 1990, "M": 1991, "N": 1992, "P": 1993, "R": 1994,
    "S": 1995, "T": 1996, "V": 1997, "W": 1998, "X": 1999,
    "Y": 2000, "1": 2001, "2": 2002, "3": 2003, "4": 2004,
    "5": 2005, "6": 2006, "7": 2007, "8": 2008, "9": 2009,
}


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


def vin_year(value):
    vin = extract_valid_vin(value)
    if not vin:
        return None
    base = VIN_YEAR_CODES.get(vin[9])
    if base is None:
        return None
    upper_bound = datetime.utcnow().year + 1
    return max(range(base, upper_bound + 1, 30), default=None)

import sys
import traceback
import json

from scourgify import normalize_address_record


def error(error: str, message: str, stacktrace: str) -> str:
    """
    Format error information as a JSON object and return it as a string.
    """

    return json.dumps({
        "error": error,
        "message": message,
        "stacktrace": stacktrace
    })


if len(sys.argv) > 1:
    try:
        address = normalize_address_record(sys.argv[1])
        print(json.dumps(address))
    except Exception as e:
        print(error("Failed to parse the address.", str(e), traceback.format_exc()))
else:
    print(error("ERR: No address given", None, None))

from collections import OrderedDict
from typing import OrderedDict
import json


def error_dict(error: str, message: str = None, stacktrace: str = None) -> OrderedDict[str, str]:
    """
    This takes the elements of an error message and puts them in an ordered dictionary.
    That dictionary can later be used by format_error() to be put in a JSON-encoded
    string.

    Args:
        error: The main error message summary.
        message: The message provided by an actual exception (optional).
        stacktrace: The exception's stacktrace, for debugging purposes (optional).

    Returns:
        The error message elements in a dictionary.
    """

    return OrderedDict([
        ('error', error),
        ('message', message),
        ('stacktrace', stacktrace)
    ])


def format_error(error: str, message: str = None, stacktrace: str = None) -> str:
    """
    Format error information as a JSON object and return it as a string.

    Args:
        error: The main error message summary.
        message: The message provided by an actual exception (optional).
        stacktrace: The exception's stacktrace, for debugging purposes (optional).

    Returns:
        The JSON-encoded error message.
    """

    return json.dumps(error_dict(error, message, stacktrace))

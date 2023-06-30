from typing import List, OrderedDict, Union
from collections import OrderedDict
import json


def error_dict(error: str,
               message: str = None,
               stacktrace: str = None) -> OrderedDict[str, Union[str, None]]:
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

    return json.dumps(error_dict(error, message, stacktrace), indent=2)


def define_address(address_line_1: Union[str, None],
                   address_line_2: Union[str, None],
                   city: Union[str, None],
                   state: Union[str, None],
                   postal_code: Union[str, None],
                   error: Union[str, None] = None,
                   normalized: Union[str, None] = None) -> OrderedDict[str, Union[str, None]]:
    """
    Manually define a scourgify-style address dictionary.

    Args:
        address_line_1: The first line of the address.
        address_line_2: The second line of the address.
        city: The city.
        state: The state.
        postal_code: The postal (zip) code.
        error: An optional error message.
        normalized: The optional normalized address, as given by format().
    """

    d = OrderedDict([
        ('address_line_1', address_line_1),
        ('address_line_2', address_line_2),
        ('city', city),
        ('state', state),
        ('postal_code', postal_code),
    ])

    if error:
        d['error'] = error

    if normalized:
        d['normalized'] = normalized

    return d


def join_parts(delimiter: str, parts: List[Union[str, None]]) -> str:
    """
    Join only non-null elements of a string array with some delimiter.

    Args:
        delimiter: The character to separate the strings with.
        parts: The strings to join.

    Returns:
        A single joined string, or None if the joined string would be empty
    """
    s = delimiter.join(filter(None, parts))

    if s:
        return s
    else:
        return None

from collections import OrderedDict
from typing import OrderedDict, List
import json
import traceback
import re

from address_utils import error_dict, format_error

from usaddress import tag
from scourgify import normalize_address_record
from scourgify.cleaning import pre_clean_addr_str, post_clean_addr_str

ADDRESS_PREFIX_REGEX = r'^((mailing?)\s*address[:;\s]*)'


def clean_address(input: str) -> str:
    """
    Clean some input address to remove a few possible artifacts. This is done
    before parsing it.

    Args:
        input: The address to clean.

    Returns:
        The cleaned address.
    """

    # Replace "null" and "none" with None
    if not input or input.lower() in ['null', 'none']:
        return None

    # Look for the prefix "address:" or "mailing address:" and remove it
    match = re.match(ADDRESS_PREFIX_REGEX, input, re.IGNORECASE)
    if match:
        input = input[match.end():].strip()

    return input


def define_address(address_line_1, address_line_2, city, state, postal_code) -> OrderedDict:
    """
    Manually define a scourgify-style address dictionary.
    """

    return OrderedDict([
        ('address_line_1', address_line_1),
        ('address_line_2', address_line_2),
        ('city', city),
        ('state', state),
        ('postal_code', postal_code),
    ])


def parse(input: str) -> OrderedDict[str, str]:
    """
    Parse an address, and return it as an ordered dictionary.

    If it cannot be parsed, this returns a error message in an ordered dictionary.

    Args:
        input: The address to parse.

    Returns:
        A dictionary with parsed address information.
    """

    # First, clean the input
    input = clean_address(input)

    # If the input is null or empty, return an empty address record
    if not input:
        return define_address(None, None, None, None, None)

    # Attempt to parse the adddress with usaddress-scourgify
    try:
        return normalize_address_record(input)
    except Exception as e:
        error = e
        stack = traceback.format_exc()

    # If that fails, parse it with usaddress
    try:
        tagged, _ = tag(input)
    except Exception as e:
        return error_dict("Failed to parse address.", f'{str(error)} | {str(e)}', stack)

    # Now take a naive approach to grouping tags. Stick everything on address_line_1 except
    # for the city, state, and postal code. Also ignore tags after those three, because it's
    # probably just the country name.
    address = define_address([], None, None, None, None)

    ignore_future_tags = False

    def clean(s):
        return post_clean_addr_str(pre_clean_addr_str(s.upper()))

    for key, value in tagged.items():
        if key == 'PlaceName':
            address['city'] = clean(value.upper())
            ignore_future_tags = True
        elif key == 'StateName':
            address['state'] = clean(value.upper())
            ignore_future_tags = True
        elif key == 'ZipCode':
            address['postal_code'] = clean(value.upper())
            ignore_future_tags = True
        elif not ignore_future_tags:
            address['address_line_1'].append(clean(value.upper()))

    address['address_line_1'] = join_parts(" ", address['address_line_1'])

    return address


def normalize(address: OrderedDict[str, str]) -> str:
    """
    Write a parsed address in the standard format:

    ADDRESS_LINE_1
    ADDRESS_LINE_2
    CITY STATE POSTAL_CODE

    Args:
        address: A parsed address.

    Returns:
        The normalized address in a single string.
    """

    if not address:
        return None

    city_line = join_parts(' ', [
        address['city'],
        address['state'],
        address['postal_code']
    ])

    return join_parts('\n', [
        address['address_line_1'],
        address['address_line_2'],
        city_line
    ])


def parse_and_normalize(input: str) -> OrderedDict[str, str]:
    """
    Parse and foramt the given address. This is done via calls to parse() and normalize(),
    respectively.

    If the parse() method fails to parse the address, instead returning an error, this
    method will return the error plainly as an OrderedDict, ready for putting in JSON.

    On the other hand, if the parsing is successful, the address is normalized and returned
    in an OrderedDict mapped to the key "normalized".

    Either way, this returns an OrderedDict.

    Args:
        input: The address to parse and normalize.

    Returns:
        An OrderedDict with the normalized address (or error message).
    """

    parsed = parse(input)

    if 'error' in parsed:
        return parsed
    else:
        return OrderedDict([("normalized", normalize(parsed))])


def compare(addr1: str, addr2: str, parsed1: OrderedDict[str, str] = None) -> OrderedDict:
    """
    Compare two addresses to determine whether they're the same. Return the results
    in an ordered dictionary.

    The dictionary will conatin the following keys:

    'match' - Either 'EXACT', 'INDICATOR', or 'NONE' depending on whether they match.
    'preference' - The preferred normalized format for the address. If they don't match
                   and a preference can't be established, this is None.
    'info' - Extra info that is sometimes included; likely this is an error message.

    Args:
        addr1: The first address.
        addr2: The second address.
        parsed1: The parsed version of add1 (optional). This is useful if making many calls
        on the same address, as it greatly improves performance.

    Returns:
        An OrderedDict with the results: an indication of whether they're the
        same and a preference for the best formatted address.
    """

    def result(m, p, i=None):
        return OrderedDict([('match', m), ('preference', p), ('info', i)])

    if addr1 == addr2:
        return result('EXACT', normalize(parse(addr1)))

    if not parsed1:
        parsed1 = parse(addr1)
    parsed2 = parse(addr2)

    # Exit if one or both addresses aren't parseable
    if 'error' in parsed1:
        if 'error' in parsed2:
            return result(
                'NONE', None,
                f'Both are un-parseable: <{json.dumps(parsed1)}> <{json.dumps(parsed2)}>'
            )
        else:
            return result(
                'NONE', normalize(parsed2),
                f'Addr1 is un-parseable: <{json.dumps(parsed1)}>'
            )
    elif 'error' in parsed2:
        return result(
            'NONE', normalize(parsed1),
            f'Addr2 is un-parseable: <{json.dumps(parsed2)}>'
        )

    normalized1 = normalize(parsed1)
    normalized2 = normalize(parsed2)

    # If normalizing made them equal, exit
    if normalized1 == normalized2:
        return result('INDICATOR', normalized1, 'Identical normalization')

    # --------------------------------------------------
    # Now for some arbitrary fixes for specific issues
    # --------------------------------------------------

    # If one has a zipcode and the other doesn't, or one has a long format zip
    # code, go with the one that has more info
    def fix_zip(a: object, b: object, b_norm: str) -> str:
        def resilient_len(s): return len(s) if s else 0

        if resilient_len(a['postal_code']) > resilient_len(b['postal_code']):
            if a['postal_code'].startswith(b['postal_code'] if b['postal_code'] else ''):
                b['postal_code'] = a['postal_code']
                return normalize(b)
        else:
            return b_norm

    normalized1 = fix_zip(parsed2, parsed1, normalized1)
    normalized2 = fix_zip(parsed1, parsed2, normalized2)

    if normalized1 == normalized2:
        return result('INDICATOR', normalized1, f'Missing/incomplete postal_code')

    # Check whether the ending of the street address got mixed up in the city name due to
    # a missing space. (e.g. "52 WOOD RD CITY ST" and "52 WOOD RDCITY ST").
    # This function checks whether 'b' is simply 'a' with bad spacing.
    def fix_city_spacing(a: object, b: object, b_norm: str) -> str:
        if a['city'] and b['city'] and a['city'] != b['city'] and a['city'] in b['city']:
            merged_1 = join_parts(
                '', [a['address_line_1'], a['address_line_2'], a['city']]
            )

            if merged_1 and merged_1.endswith(b['city']):
                b['address_line_2' if b['address_line_2'] else 'address_line_1'] += \
                    ' ' + b['city'][:-len(a['city'])]
                b['city'] = a['city']
                return normalize(b)

        return b_norm

    normalized1 = fix_city_spacing(parsed2, parsed1, normalized1)
    normalized2 = fix_city_spacing(parsed1, parsed2, normalized2)

    if normalized1 == normalized2:
        return result('INDICATOR', normalized1, f'Fixed malformed spacing')

    if (normalized1 and not normalized2) or (not normalized1 and normalized2):
        return result('NONE', normalized1 if normalized1 else normalized2, f'Preferred non-null')

    # Check whether they start with "PO BOX" but with improper spacing, and if so, fix it
    if normalized1.replace(' ', '').startswith('POBOX'):
        normalized1 = "PO BOX" + normalized1.split("X", 1)[-1]
    if normalized2.replace(' ', '').startswith('POBOX'):
        normalized2 = "PO BOX" + normalized2.split("X", 1)[-1]

    if normalized1 == normalized2:
        return result('INDICATOR', normalized1, "Improper spacing in 'PO BOX'")

    return result('NONE', None)


def join_parts(delimiter: str, parts: List[str]) -> str:
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

from typing import Union
from collections import OrderedDict
import json
import re

import utils

from usaddress import tag
from scourgify import normalize_address_record
import scourgify.normalize as sc_norm
import scourgify.cleaning as sc_clean

ADDRESS_PREFIX_REGEX = r'^((mailing)?\s*address[:;\s]*)'

LOOKUP_TABLE: list[dict[str, str]] = []


def set_lookup_table(data: Union[None, list[dict[str, str]]]) -> None:
    """
    Set the lookup data. This is a list of dictionaries, each with the following keys:
        - 'raw': The raw address to check against.
        - 'address_line_1'
        - 'address_line_2'
        - 'city'
        - 'state'
        - 'postal_code'

    Each dictionary provides the normalization for some address that would otherwise be unparseable
    by this library, thereby allowing for manual overrides.

    This will automatically trim the 'raw' value and make it lowercase, as comparisons to the raw
    value are case-insensitive and ignore surrounding whitespace.

    If the given data is malformed, this will catch it and throw an exception.

    Args:
        data: The lookup table entries, or None if there isn't any data.

    Raises:
        TypeError: If the data is neither None nor a list of dictionaries.
        ValueError: If any dictionary in the list is None or does not contain
        the required keys, or the 'raw' key is None

    Returns:
        None
    """

    global LOOKUP_TABLE

    if not data:
        return

    # Check data types
    if not isinstance(data, list) or not all(isinstance(item, dict) for item in data):
        raise TypeError(
            "The lookup table must be a file with a JSON array of objects.")

    # Check for required keys
    expected_keys = ('raw', 'address_line_1', 'address_line_2',
                     'city', 'state', 'postal_code')
    for item in data:
        if item is None:
            raise ValueError("Lookup table entry must not be null")
        for key in expected_keys:
            if key not in item:
                raise ValueError(f"Lookup table entry must have key {key}")
        if not item['raw']:
            raise ValueError("The 'raw' key must not be null")

        item['raw'] = item['raw'].strip().lower()
        LOOKUP_TABLE.append(item)


def _clean_input(input: Union[str, None]) -> Union[str, None]:
    """
    Clean any input given to this program. This does the following:
     
    1. Replace the strings "none" and "null" with None.
    2. Strip leading and trailing whitespace.
    3. Remove null characters and control characters.

    Args:
        input: The input to clean.

    Returns:
        The cleaned input.
    """

    if not input:
        return None
    
    # Strip whitespace and remove null characters. The RegEx comes from ChatGPT
    input = input.strip().replace('\x00', '')
    input = re.sub(r'[^\x20-\x7E]', '', input)

    # Replace "null" and "none" with None
    if not input or input.lower() in ['null', 'none']:
        return None
    else:
        return input


def _clean_address(input: Union[str, None]) -> Union[str, None]:
    """
    Clean some input address to remove a few possible artifacts. This is done
    before parsing it.

    Args:
        input: The address to clean.

    Returns:
        The cleaned address.
    """

    # First clean the input generally
    input = _clean_input(input)

    if not input:
        return None

    # Look for the prefix "address:" or "mailing address:" and remove it
    match = re.match(ADDRESS_PREFIX_REGEX, input, re.IGNORECASE)
    if match:
        input = input[match.end():].strip()

    # Replace any line breaks with commas. This seems to fix some parsing errors that result
    # from parsing, normalizing, and parsing an address again. For example, the address
    # '1234 SOMEWHERE ROAD, NORTH CHARLESTON SC' and '1234 SOMEWHERE ROAD\nNORTH CHARLESTON SC'
    # are parsed differently without this step. (The latter makes NORTH part of the road,
    # rather than part of the city, as it should).
    input = input.replace("\n", ", ")

    return input


def _parse_usaddress(address: Union[str, None]) -> OrderedDict[str, Union[str, None]]:
    """
    Parse a particularly difficult address with usaddress, coercing it into
    the scourgify format.

    Args:
        address: The address to parse.

    Returns:
        A dictionary with parsed address information. Alternatively, this
        might throw an exception.
    """

    tagged, _ = tag(address)

    # If the list of tags is empty, there's no address
    if len(tagged) == 0:
        return utils.define_address(None, None, None, None, None)

    # Now take a naive approach to grouping tags. Stick everything on address_line_1 except
    # for the city, state, and postal code. Also ignore tags after those three, because it's
    # probably just the country name.
    parsed = utils.define_address([], [], None, None, None)

    current_line = 1
    ignore_future_tags = False

    def clean(s):
        return sc_clean.post_clean_addr_str(sc_clean.pre_clean_addr_str(s.upper()))

    for key, value in tagged.items():
        if key == 'PlaceName':
            parsed['city'] = clean(value)
            ignore_future_tags = True
        elif key == 'StateName':
            parsed['state'] = clean(value)
            ignore_future_tags = True
        elif key == 'ZipCode':
            parsed['postal_code'] = clean(value)
            ignore_future_tags = True
        elif key == 'Recipient':
            parsed['address_line_1'].append(clean(value))
            current_line = 2
        elif not ignore_future_tags:
            parsed[f'address_line_{current_line}'].append(clean(value))

    # Combine the arrays
    for line in ['address_line_1', 'address_line_2']:
        parsed[line] = utils.join_parts(" ", parsed[line])

    # Normalize the state and city
    parsed['state'] = sc_norm.normalize_state(parsed['state'])
    parsed['city'] = sc_norm.normalize_city(parsed['city'])

    return parsed


def address(input: Union[str, None]) -> OrderedDict[str, Union[str, None]]:
    """
    Parse an address, and return it as an ordered dictionary. This will always contain the
    following keys:

    address_line_1: (Nullable) The first line
    address_line_2: (Nullable) The second line
    city:           (Nullable) The city
    state:          (Nullable) The state
    postal_code:    (Nullable) The postal code

    It may also contain the key 'error', which will contain an error message. If this key
    is present, then the other keys will all be None.

    Args:
        input: The address to parse.

    Returns:
        A dictionary with parsed address information.
    """

    # First, clean the input
    cleaned = _clean_address(input)

    # Check both the cleaned and raw address against the lookup table for a quick match
    input_trimmed = None if not input else input.strip().lower()
    cleaned_trimmed = None if not cleaned else cleaned.strip().lower()
    for entry in LOOKUP_TABLE:
        if cleaned_trimmed == entry['raw'] or input_trimmed == entry['raw']:
            return utils.define_address(entry['address_line_1'], entry['address_line_2'],
                                        entry['city'], entry['state'], entry['postal_code'])

    # If the cleaned input is null or empty, return an empty address record
    if not cleaned:
        return utils.define_address(None, None, None, None, None)

    errors = []
    parsed = None

    # Attempt to parse the adddress with usaddress-scourgify
    try:
        parsed = normalize_address_record(cleaned)
    except Exception as e:
        errors.append(e)

    # If that fails, parse it with usaddress
    try:
        parsed = _parse_usaddress(cleaned)
    except Exception as e:
        errors.append(e)

    # If unable to parse, exit
    if not parsed:
        return utils.define_address(
            None, None, None, None, None,
            f"Failed to parse '{input}': '{errors[0]}' and '{errors[1]}'"
        )

    # If the address starts with "PO BOX" but with improper spacing, fix it
    if parsed['address_line_1'] and parsed['address_line_1'].replace(' ', '').startswith('POBOX'):
        parsed['address_line_1'] = "PO BOX" + \
            parsed['address_line_1'].split('X', 1)[-1]

    return parsed


def format(address: OrderedDict[str, Union[str, None]]) -> Union[str, None]:
    """
    Write a parsed address in this standard format:

    ADDRESS_LINE_1
    ADDRESS_LINE_2
    CITY, STATE POSTAL_CODE

    If the given address is None or contains the key 'error', this returns None.

    Args:
        address: A parsed address.

    Returns:
        The normalized address in a single string.
    """

    if not address or 'error' in address:
        return None

    city_line = utils.join_parts(', ', [address['city'], address['state']])
    city_line = utils.join_parts(' ', [city_line, address['postal_code']])

    return utils.join_parts('\n', [
        address['address_line_1'],
        address['address_line_2'],
        city_line
    ])


def _package_norm(normalized: Union[str, None],
                  address: Union[str, None],
                  address_value: Union[str, None],
                  error: Union[str, None] = None) -> OrderedDict[str, Union[str, None]]:
    d = OrderedDict([
        ('normalized', normalized),
        ('address', address),
        ('address_value', address_value)
    ])

    if error:
        d['error'] = error

    return d


def city(city: Union[str, None],
         addr: Union[str, None]) -> OrderedDict[str, Union[str, None]]:
    """
    Attempt to normalize a city. Use the given address string to try to help.

    Args:
        city: The current city string to normalize.
        addr: The current address, which might have more useful information.

    Return:
        An OrderedDict with the normalization output.
    """

    city = _clean_input(city)
    parsed = address(addr)
    parsed_city = parsed['city']
    parsed_norm = format(parsed)
    norm_city = sc_norm.normalize_city(
        sc_clean.clean_upper(city)) if city else None

    # If there's no normalized state, use the one from the address
    if not norm_city:
        if parsed_city:
            return _package_norm(parsed_city, parsed_norm, parsed_city)
        else:
            return _package_norm(None, parsed_norm, None, parsed.get('error'))

    # If there's no state from the address, use the normalized state
    if not parsed_city:
        return _package_norm(norm_city, parsed_norm, None, parsed.get('error'))

    # If the states are identical, use that
    if norm_city == parsed_city:
        return _package_norm(norm_city, parsed_norm, parsed_city)

    # The states don't match, and there's no clear means of resolution
    return _package_norm(None, parsed_norm, parsed_city)


def state(state: Union[str, None],
          addr: Union[str, None]) -> OrderedDict[str, Union[str, None]]:
    """
    Attempt to normalize a state. Use the given address string to try to help.

    Args:
        state: The current state string to normalize.
        addr: The current address, which might have more useful information.

    Return:
        An OrderedDict with the normalization output.
    """

    state = _clean_input(state)
    parsed = address(addr)
    parsed_state = parsed['state']
    parsed_norm = format(parsed)
    norm_state = sc_norm.normalize_state(state) if state else None

    # If there's no normalized state, use the one from the address
    if not norm_state:
        if parsed_state:
            return _package_norm(parsed_state, parsed_norm, parsed_state)
        else:
            return _package_norm(None, parsed_norm, None, parsed.get('error'))

    # If there's no state from the address, use the normalized state
    if not parsed_state:
        return _package_norm(norm_state, parsed_norm, None, parsed.get('error'))

    # If the states are identical, use that
    if norm_state == parsed_state:
        return _package_norm(norm_state, parsed_norm, parsed_state)

    # The states don't match, and there's no clear means of resolution
    return _package_norm(None, parsed_norm, parsed_state)

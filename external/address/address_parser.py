from typing import OrderedDict, Tuple, Union
from collections import OrderedDict
import re

from address_utils import *

from usaddress import tag
from scourgify import normalize_address_record
from scourgify.cleaning import pre_clean_addr_str, post_clean_addr_str
from scourgify.normalize import normalize_state, normalize_city

ADDRESS_PREFIX_REGEX = r'^((mailing?)\s*address[:;\s]*)'


def _clean_address(input: Union[str, None]) -> Union[str, None]:
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
    
    input = input.strip()
    if not input:
        return None

    # Look for the prefix "address:" or "mailing address:" and remove it
    match = re.match(ADDRESS_PREFIX_REGEX, input, re.IGNORECASE)
    if match:
        input = input[match.end():].strip()

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
        return define_address(None, None, None, None, None)

    # Now take a naive approach to grouping tags. Stick everything on address_line_1 except
    # for the city, state, and postal code. Also ignore tags after those three, because it's
    # probably just the country name.
    parsed = define_address([], [], None, None, None)

    current_line = 1
    ignore_future_tags = False

    def clean(s):
        return post_clean_addr_str(pre_clean_addr_str(s.upper()))

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
        parsed[line] = join_parts(" ", parsed[line])

    # Normalize the state and city
    parsed['state'] = normalize_state(parsed['state'])
    parsed['city'] = normalize_city(parsed['city'])

    return parsed


def parse(input: Union[str, None]) -> OrderedDict[str, Union[str, None]]:
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

    # If the input is null or empty, return an empty address record
    if not cleaned:
        return define_address(None, None, None, None, None)

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
        return define_address(
            None, None, None, None, None,
            f"Failed to parse '{input}': '{errors[0]}' and '{errors[1]}'"
        )

    # If the address starts with "PO BOX" but with improper spacing, fix it
    if parsed['address_line_1'] and parsed['address_line_1'].replace(' ', '').startswith('POBOX'):
        parsed['address_line_1'] = "PO BOX" + \
            parsed['address_line_1'].split('X', 1)[-1]

    return parsed


def normalize(address: OrderedDict[str, Union[str, None]]) -> Union[str, None]:
    """
    Write a parsed address in the standard format:

    ADDRESS_LINE_1
    ADDRESS_LINE_2
    CITY STATE POSTAL_CODE

    If the given address is None or contains the key 'error', this returns None.

    Args:
        address: A parsed address.

    Returns:
        The normalized address in a single string.
    """

    if not address or 'error' in address:
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


def compare(addr1: Union[str, None],
            addr2: Union[str, None],
            parsed1: OrderedDict[str, Union[str, None]] = None) -> OrderedDict[str, Union[str, None]]:
    """
    Compare two addresses to determine whether they're the same. Return the results
    in an ordered dictionary.

    Args:
        addr1: The first address.
        addr2: The second address.
        parsed1: The parsed version of addr1 (optional). This is useful if making many calls
        on the same address, as it greatly improves performance.

    Returns:
        An OrderedDict with the results: an indication of whether they're the
        same and a preference for the best formatted address.
    """

    if not parsed1:
        parsed1 = parse(addr1)

    if addr1 == addr2:
        return _package_comp_result('EXACT', parsed1, normalize(parsed1))

    parsed2 = parse(addr2)

    # Exit if one or both addresses aren't parseable
    if 'error' in parsed1:
        if 'error' in parsed2:
            return _package_comp_result(
                'NONE', None, None,
                f"Both are un-parseable: '{parsed1['error']}' '{parsed2['error']}'"
            )
        else:
            return _package_comp_result(
                'NONE', parsed2, normalize(parsed2),
                f"Addr1 not parseable: '{parsed1['error']}'"
            )
    elif 'error' in parsed2:
        return _package_comp_result(
            'NONE', parsed1, normalize(parsed1),
            f"Addr2 not parseable: '{parsed2['error']}'"
        )

    norm1 = normalize(parsed1)
    norm2 = normalize(parsed2)

    # If normalizing made them equal, exit
    if norm1 == norm2:
        return _package_comp_result('INDICATOR', parsed1, norm1, 'Identical normalization')

    # If only one is null, go with the non-null one
    if (norm1 and not norm2) or (not norm1 and norm2):
        return _package_comp_result(
            'NONE',
            parsed1 if norm1 else parsed2,
            norm1 if norm1 else norm2, f'Preferred non-null'
        )

    # --------------------------------------------------
    # Now for some arbitrary fixes for specific issues
    # --------------------------------------------------

    functions = [(_fix_zip, 'Mising/incomplete postal_code'),
                 (_fix_city_spacing, 'Malformed spacing'),
                 (_fix_missing_info, 'Missing city, state, or recipient')]

    for func, info in functions:
        parsed1, norm1 = func(parsed2, parsed1, norm1)
        parsed2, norm2 = func(parsed1, parsed2, norm2)

        if norm1 == norm2:
            return _package_comp_result('INDICATOR', parsed1, norm1, info)

    # At this point, they definitively don't match
    return _package_comp_result('NONE', None, None)


def _package_comp_result(match: str,
                         p_par: OrderedDict[str, Union[str, None]],
                         p_norm: Union[str, None],
                         info: Union[str, None] = None) -> OrderedDict[str, Union[str, None]]:
    """
    Given the elements returned by the compare() function, package them into a single
    OrderedDict.

    Args:
        match: The string indicating the match result (must be non-null).
        p_par: The preferred address, parsed.
        p_norm: The preferred address, normalized.
        info: Optional additional information related to the comparison (for debugging).

    Returns:
        A single OrderedDict containing all the necessary information.
    """

    if p_par and p_norm:
        p_par['normalized'] = p_norm

    return OrderedDict([
        ('match', match),
        ('address_line_1', p_par['address_line_1'] if p_par else None),
        ('address_line_2', p_par['address_line_2'] if p_par else None),
        ('city', p_par['city'] if p_par else None),
        ('state', p_par['state'] if p_par else None),
        ('postal_code', p_par['postal_code'] if p_par else None),
        ('normalized', p_norm if p_norm else None),
        ('info', info)
    ])


def _fix_zip(p1: OrderedDict[str, Union[str, None]],
             p2: OrderedDict[str, Union[str, None]],
             n2: Union[str, None]) -> Tuple[OrderedDict[str, Union[str, None]], str]:
    """
    Check to see if one zip code is a subset of the other zipcode. In that case, update
    the incomplete zip code.

    Args:
        p1: The first address (the one assumed to be correct) parsed.
        p2: The second address parsed.
        n2: The second address normalized.

    Returns: A tuple of the second address parsed and normalized. These may be the same
    as the input, or they may have changed to fix the zip code.
    """
    # 'a' is the better one here; 'b' is the incomplete one

    if p1['postal_code']:
        if not p2['postal_code'] or p1['postal_code'].startswith(p2['postal_code']):
            p2['postal_code'] = p1['postal_code']
            return p2, normalize(p2)

    return p2, n2


def _fix_city_spacing(p1: OrderedDict[str, Union[str, None]],
                      p2: OrderedDict[str, Union[str, None]],
                      n2: Union[str, None]) -> Tuple[OrderedDict[str, Union[str, None]], str]:
    """
    Check to see if the ending of the street address got mixed up in the city name for one
    of the addresses due to a missing space. In that case, fix the messed up one.

    Args:
        p1: The first address (the one assumed to be correct) parsed.
        p2: The second address parsed.
        n2: The second address normalized.

    Returns: A tuple of the second address parsed and normalized. These may be the same
    as the input, or they may have changed to fix the spacing.
    """

    if p1['city'] and p2['city'] and p1['city'] != p2['city'] and p1['city'] in p2['city']:
        merged_1 = join_parts(
            '', [p1['address_line_1'], p1['address_line_2'], p1['city']]
        )

        if merged_1 and merged_1.endswith(p2['city']):
            last_line = 'address_line_2' if p2['address_line_2'] else 'address_line_1'

            p2[last_line] += ' ' + p2['city'][:-len(p1['city'])]
            p2['city'] = p1['city']

            return p2, normalize(p2)

    return p2, n2


def _fix_missing_info(p1: OrderedDict[str, Union[str, None]],
                      p2: OrderedDict[str, Union[str, None]],
                      n2: Union[str, None]) -> Tuple[OrderedDict[str, Union[str, None]], str]:
    """
    Check to see if one address has extra info (the city and state) while the other doesn't.
    In that case, add the missing information.

    This also checks whether p1 has a recipient listed which moved line1 to line2, offsetting
    the match.

    Args:
        p1: The first address (the one assumed to be correct) parsed.
        p2: The second address parsed.
        n2: The second address normalized.

    Returns: A tuple of the second address parsed and normalized. These may be the same
    as the input, or they may have changed to add the missing information.
    """

    # If p2 is missing the city, add it
    if p1['city'] and not p2['city']:
        p2['city'] = p1['city']
        n2 = normalize(p2)

    # If p2 is missing the state, add it
    if p1['state'] and not p2['state']:
        p2['state'] = p1['state']
        n2 = normalize(p2)

    # If p2 has line1 moved to line2, then add the missing line1 to p2
    if p1['address_line_2'] and p1['address_line_2'] == p2['address_line_1']:
        p2['address_line_1'] = p1['address_line_1']
        p2['address_line_2'] = p1['address_line_2']
        n2 = normalize(p2)

    return p2, n2

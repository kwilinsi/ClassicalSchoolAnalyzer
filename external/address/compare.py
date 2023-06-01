from typing import OrderedDict, Tuple, Union
from collections import OrderedDict

import utils
import normalize


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
        parsed1 = normalize.address(addr1)

    if addr1 == addr2:
        return _package_comp_result('EXACT', parsed1, normalize(parsed1))

    parsed2 = normalize.address(addr2)

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
        merged_1 = utils.join_parts(
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

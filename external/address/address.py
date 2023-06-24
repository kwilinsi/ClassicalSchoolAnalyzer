from typing import Any, OrderedDict, Union

import sys
import os
import json
import traceback
from pathlib import Path
from collections import OrderedDict

import utils
import normalize
import compare


def main():
    """
    This main function processes the input to the program and prints the appropriate output
    to the console.
    """

    # Ensure arguments given
    _validate_args(2, 'Missing task. See README.')
    _validate_args(3, 'Missing arguments. See README.')

    # Parse command line addresses
    try:
        if sys.argv[1] == 'normalize':
            run_normalize()
        elif sys.argv[1] == 'normalize_file':
            run_normalize_file()
        elif sys.argv[1] == 'normalize_city':
            run_normalize_city()
        elif sys.argv[1] == 'normalize_city_file':
            run_normalize_city_file()
        elif sys.argv[1] == 'normalize_state':
            run_normalize_state()
        elif sys.argv[1] == 'normalize_state_file':
            run_normalize_state_file()
        elif sys.argv[1] == 'compare':
            run_compare()
        elif sys.argv[1] == 'compare_file':
            run_compare_file()
        else:
            print(utils.format_error('Invalid task. See README.'))
            sys.exit(1)
    except Exception as e:
        print(utils.format_error(
            'IMPORTANT! — Fatal unexpected error',
            str(e), traceback.format_exc()
        ))


def run_normalize():
    """
    Parse and normalize individual addresses passed via command line.
    """

    parsed = []
    for address in sys.argv[2:]:
        parsed.append(_parse_normalize(address))

    print(json.dumps(parsed))


def run_normalize_file():
    """
    Normalize all the addresses in a file.
    """

    input_path = sys.argv[2]
    output_path = _get_output_path(input_path, 'normalized')

    input_data = _parse_json(input_path)

    if input_data:
        output_data = [_parse_normalize(address) for address in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def run_normalize_city():
    """
    Normalize some city by comparing it with an address.
    """

    _validate_args(4, 'Missing the address. See README for documentation.')

    print(json.dumps(normalize.city(sys.argv[2], sys.argv[3])))


def run_normalize_city_file():
    """
    Normalize many city values by comparring them with addresses.
    """

    input_path = sys.argv[2]
    output_path = _get_output_path(input_path, 'normalized')

    input_data = _parse_json(input_path)

    if input_data:
        output_data = [normalize.city(
            entry['value'], entry['address']) for entry in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def run_normalize_state():
    """
    Normalize some state by comparing it with an address.
    """

    _validate_args(4, 'Missing the address. See README for documentation.')

    print(json.dumps(normalize.state(sys.argv[2], sys.argv[3])))


def run_normalize_state_file():
    """
    Normalize many state values by comparring them with addresses.
    """

    input_path = sys.argv[2]
    output_path = _get_output_path(input_path, 'normalized')

    input_data = _parse_json(input_path)

    if input_data:
        output_data = [normalize.state(
            entry['value'], entry['address']) for entry in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def run_compare():
    """
    Compare two addresses.
    """

    _validate_args(
        4, 'Missing the second address. See README for documentation.')

    print(json.dumps(compare.compare_address(sys.argv[2], sys.argv[3])))


def run_compare_file():
    """
    Compare a given address to every address in a file.
    """

    _validate_args(4, 'Missing the file path. See README for documentation.')

    addr1 = sys.argv[2]
    if addr1 == 'null':
        addr1 = None
        
    parsed1 = normalize.address(addr1)

    input_path = sys.argv[3]
    output_path = _get_output_path(input_path, 'compared')

    input_data = _parse_json(input_path)

    if input_data:
        output_data = [compare.compare_address(addr1, address, parsed1)
                       for address in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def _validate_args(n: int, message: str) -> None:
    """
    Ensure there are enough user-provided command line arguments. If there aren't, print
    an error message and exit.

    Args:
        n: The minimum allowed number of arguments.
        message: The error message to print if there aren't enough arguments.

    Returns:
        None
    """

    if len(sys.argv) < n:
        print(utils.format_error(message))
        sys.exit(1)


def _get_output_path(input: str, suffix: str) -> str:
    """
    Given an input file to read, determine the output file path.

    Args:
        input: The path to the input file.
        suffix: The string to append to the input file name for determining the output path.

    Returns:
        The output file path.
    """

    return os.path.join(
        os.path.dirname(input),
        f'{Path(input).stem}_{suffix}.json'
    )


def _parse_normalize(address) -> OrderedDict[str, Union[str, None]]:
    """
    Parse and normalize an address.

    Args:
        address: The address to parse and normalize.

    Returns:
        An OrderedDict with the parse and normalization information.
    """

    p = normalize.address(address)

    if 'error' not in p:
        p['normalized'] = normalize.format(p)

    return p


def _parse_json(path: str) -> Any:
    """
    Open the given file path, and parse its contents as JSON. Return the result.

    If the file is empty, this returns None. If for some other reason the parsing
    fails, a message is printed and the program exits with code 1.

    Args:
        path: The path of the file to read.

    Returns:
        The parsed JSON data (or None if the file is empty).
    """

    try:
        with open(path, 'r') as input_file:
            try:
                return json.load(input_file)
            except json.JSONDecodeError as e:
                # If the file is empty, that's fine. Otherwise, log the error.
                if input_file.tell() == 0:
                    return None
                else:
                    print(utils.format_error(f'Malformed JSON data in {path}',
                                             str(e), traceback.format_exc()))
    except OSError as e:
        print(utils.format_error(f"Couldn't open {path}",
                                 str(e), traceback.format_exc()))

    sys.exit(1)


def _save_json(path: str, data: Any) -> None:
    """
    Save the given JSON data to the given file. If this is successful, a JSON object
    with the single key "output_file" and value of the path is printed to the console.

    Otherwise, if an error occurs, it is printed, and the program exits with code 1.

    Args:
        path: The path to the output file.
        data: The data to encode as JSON and save.

    Returns:
        None
    """

    try:
        # Save the resulting JSON array to the output file
        with open(path, 'w') as file:
            json.dump(data, file)

        print(json.dumps({"output_file": path}))
        return

    except OSError as e:
        print(utils.format_error(f"Couldn't save output to {path}",
              str(e), traceback.format_exc()))
    except Exception as e:
        print(utils.format_error(f"Encounted an error while saving output to {path}",
              str(e), traceback.format_exc()))

    sys.exit(1)


if __name__ == '__main__':
    main()

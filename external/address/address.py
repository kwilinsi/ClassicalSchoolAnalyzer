from typing import Any, Union

import argparse
from collections import OrderedDict
import json
import os
from pathlib import Path
import sys
import traceback

import utils
import normalize
import compare


def main():
    """
    This main function processes the input to the program and prints the appropriate output
    to the console.
    """

    # Parse the command line arguments
    args = _parse_args()

    # Run the appropriate task, first validating the number of arguments
    try:
        # Set the lookup data, if given
        if args.lookup:
            normalize.set_lookup_table(_parse_json(args.lookup))

        # ----- Address normalization -----
        if args.task == 'normalize':
            _validate_args(args, 1, 'Missing address to normalize.')
            run_normalize(args.args)
        elif args.task == 'normalize_file':
            _validate_args(args, 1,
                           'Missing file with addresses to normalize.', True)
            run_normalize_file(args.args[0])

        # ----- City normalization -----
        elif args.task == 'normalize_city':
            _validate_args(args, 1, 'Missing city to normalize.')
            _validate_args(args, 2,
                           'Missing address to assist with city normalization.', True)
            run_normalize_city(args.args[0], args.args[1])
        elif args.task == 'normalize_city_file':
            _validate_args(args, 1,
                           'Missing file with cities to normalize.', True)
            run_normalize_city_file(args.args[0])

        # ----- State normalization -----
        elif args.task == 'normalize_state':
            _validate_args(args, 1, 'Missing state to normalize.')
            _validate_args(args, 2,
                           'Missing address to assist with state normalization.', True)
            run_normalize_state(args.args[0], args.args[1])
        elif args.task == 'normalize_state_file':
            _validate_args(args, 1,
                           'Missing file with states to normalize.', True)
            run_normalize_state_file(args.args[0])

        # ----- Address comparison -----
        elif args.task == 'compare':
            _validate_args(args, 2,
                           'Exactly 2 addresses required for comparison.', True)
            run_compare(args.args[0], args.args[1])
        elif args.task == 'compare_file':
            _validate_args(args, 1, 'Missing main address to compare.')
            _validate_args(args, 2,
                           'Missing file with addresses to compare.', True)
            run_compare_file(args.args[0], args.args[1])

    except Exception as e:
        print(utils.format_error(
            'IMPORTANT! — Fatal unexpected error',
            str(e), traceback.format_exc()
        ))


def _parse_args() -> argparse.Namespace:
    """
    Parse the command line arguments to this script using the standard configuration. If the user inputs
    invalid arguments or uses the help (-h) flag, this will terminate the program.

    Returns:
        The parsed arguments.
    """

    parser = argparse.ArgumentParser(prog='address.exe',
                                     description='Parse addresses and address-related values (like city and state names) with ease.')
    parser.add_argument('--task', '-t', required=True,
                        choices=['normalize', 'normalize_file', 'normalize_city', 'normalize_city_file',
                                 'compare_state', 'normalize_state_file', 'compare', 'compare_file'],
                        help='Specify the task to perform')
    parser.add_argument('--lookup', '-l', required=False,
                        help='Specify a lookup JSON file with manually specified normalizations for addresses')
    parser.add_argument('args', nargs='*',
                        help='The arguments to pass to the task function')

    return parser.parse_args()


def _validate_args(args: argparse.Namespace, n: int, message: str, exact: bool = False) -> None:
    """
    Ensure there are enough user-provided command line arguments for the task. If there aren't, print
    an error message and exit.

    Args:
        args: The parsed arguments from argparse.
        n: The minimum allowed number of arguments.
        message: The error message to print if there aren't enough arguments.
        exact: Whether the number of arguments needs to exactly equal n or merely be greater than or equal to.

    Returns:
        None
    """

    if len(args.args) < n or (exact and len(args.args) > n):
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
        with open(path, 'r', encoding='utf-8') as input_file:
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


def run_normalize(addresses: list[str]) -> None:
    """
    Parse and normalize individual addresses passed via command line.

    Args:
        addresses: A list of one or more addresses to normalize.

    Returns:
        None
    """

    print(json.dumps([_parse_normalize(a) for a in addresses]))


def run_normalize_file(file: str) -> None:
    """
    Normalize all the addresses in a file.

    Args:
        file: The path to the file with the addresses to normalize.

    Returns:
        None
    """

    output_path = _get_output_path(file, 'normalized')
    input_data = _parse_json(file)

    if input_data:
        output_data = [_parse_normalize(address) for address in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def run_normalize_city(city: str, address: str) -> None:
    """
    Normalize some city by comparing it with an address.

    Args:
        city: The city to normalize.
        address: The address from the same source with possible information to assist in normalization.

    Returns:
        None
    """

    print(json.dumps(normalize.city(city, address)))


def run_normalize_city_file(file: str) -> None:
    """
    Normalize many city values by comparring them with addresses.

    Args:
        file: The path to the file with the cities to normalize.

    Returns:
        None
    """

    output_path = _get_output_path(file, 'normalized')
    input_data = _parse_json(file)

    if input_data:
        output_data = [normalize.city(entry['value'], entry['address'])
                       for entry in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def run_normalize_state(state: str, address: str) -> None:
    """
    Normalize some state by comparing it with an address.

    Args:
        state: The state to normalize.
        address: The address from the same source with possible information to assist in normalization.

    Returns:
        None
    """

    print(json.dumps(normalize.state(state, address)))


def run_normalize_state_file(file: str) -> None:
    """
    Normalize many state values by comparring them with addresses.

    Args:
        file: The path to the file with the states to normalize.

    Returns:
        None
    """

    output_path = _get_output_path(file, 'normalized')
    input_data = _parse_json(file)

    if input_data:
        output_data = [normalize.state(entry['value'], entry['address'])
                       for entry in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


def run_compare(address1: str, address2: str) -> None:
    """
    Compare two addresses.

    Args:
        address1: The first address.
        address2: The second address.

    Returns:
        None
    """

    print(json.dumps(compare.compare_address(address1, address2)))


def run_compare_file(address: str, file: str) -> None:
    """
    Compare a given address to every address in a file.

    Args:
        address: The address to compare to the other addresses in the file.
        file: The path to the file with the addresses to compare.

    Returns:
        None
    """

    parsed1 = normalize.address(address)
    output_path = _get_output_path(file, 'compared')
    input_data = _parse_json(file)

    if input_data:
        output_data = [compare.compare_address(address, addr2, parsed1)
                       for addr2 in input_data]
    else:
        output_data = None

    _save_json(output_path, output_data)


if __name__ == '__main__':
    main()

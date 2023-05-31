import sys
import os
import json
import traceback
from pathlib import Path
from collections import OrderedDict

from address_utils import format_error
import address_parser


def main():
    """
    This main function processes the input to the program and prints the appropriate output
    to the console.
    """

    # Ensure arguments given
    if len(sys.argv) < 3:
        if len(sys.argv) < 2:
            print(format_error("Missing task. See README documentation."))
        else:
            print(format_error("Missing arguments. See README documentation."))
        sys.exit(1)

    # Parse command line addresses
    if sys.argv[1] == 'normalize':
        normalize()
    elif sys.argv[1] == 'normalize_file':
        normalize_file()
    elif sys.argv[1] == 'compare':
        compare()
    elif sys.argv[1] == 'compare_file':
        compare_file()


def parse_normalize(address) -> OrderedDict:
    """
    Parse and normalize an address.
    """

    p = address_parser.parse(address)

    if 'error' not in p:
        p['normalized'] = address_parser.normalize(p)

    return p


def normalize():
    """
    Parse and normalize individual addresses passed via command line.
    """

    parsed = []
    for address in sys.argv[2:]:
        parsed.append(parse_normalize(address))

    print(json.dumps(parsed))


def normalize_file():
    """
    Normalize all the addresses in a file.
    """

    try:
        input_path = sys.argv[2]
        output_path = os.path.join(
            os.path.dirname(input_path),
            f'{Path(input_path).stem}_normalized.json'
        )
    except Exception as e:
        print(format_error("Malformed input: unable to identify file paths",
                           str(e), traceback.format_exc()))

    try:
        with open(input_path, 'r') as input_file:
            try:
                input_data = json.load(input_file)
            except json.JSONDecodeError as e:
                # If the file is empty, that's fine. Otherwise, log the error.
                if input_file.tell() == 0:
                    input_data = None
                else:
                    print(format_error("Malformed JSON data in input file",
                                       str(e), traceback.format_exc()))

        if input_data:
            output_data = [parse_normalize(address) for address in input_data]
        else:
            output_data = None

        # Save the resulting JSON array to the output file
        with open(output_path, 'w') as output_file:
            json.dump(output_data, output_file)

        print(json.dumps({"output_file": output_path}))

    except FileNotFoundError as e:
        print(format_error("Input file not found", str(e), traceback.format_exc()))
    except json.JSONDecodeError as e:
        print(format_error("Malformed JSON data in input file",
              str(e), traceback.format_exc()))
    except Exception as e:
        print(format_error("An error occurred", str(e), traceback.format_exc()))


def compare():
    """
    Compare two addresses.
    """

    if len(sys.argv) < 4:
        print(format_error("Missing the second address. See README for documentation."))
        sys.exit(1)

    print(json.dumps(address_parser.compare(sys.argv[2], sys.argv[3])))


def compare_file():
    """
    Compare a given address to every address in a file.
    """

    if len(sys.argv) < 4:
        print(format_error("Missing the file path. See README for documentation."))
        sys.exit(1)

    try:
        addr1 = sys.argv[2]
        parsed1 = address_parser.parse(addr1)
        input_path = sys.argv[3]
        output_path = os.path.join(
            os.path.dirname(input_path),
            f'{Path(input_path).stem}_compared.json'
        )
    except Exception as e:
        print(format_error("Malformed input: unable to identify file paths",
                           str(e), traceback.format_exc()))

    try:
        with open(input_path, 'r') as input_file:
            try:
                input_data = json.load(input_file)
            except json.JSONDecodeError as e:
                # If the file is empty, that's fine. Otherwise, log the error.
                if input_file.tell() == 0:
                    input_data = None
                else:
                    print(format_error("Malformed JSON data in input file",
                                       str(e), traceback.format_exc()))

        if input_data:
            output_data = [address_parser.compare(addr1, address, parsed1)
                           for address in input_data]
        else:
            output_data = None

        # Save the resulting JSON array to the output file
        with open(output_path, 'w') as output_file:
            json.dump(output_data, output_file)

        print(json.dumps({"output_file": output_path}))

    except FileNotFoundError as e:
        print(format_error("Input file not found", str(e), traceback.format_exc()))
    except Exception as e:
        print(format_error("An error occurred", str(e), traceback.format_exc()))


if __name__ == '__main__':
    main()

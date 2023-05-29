# Python Address Parser

This directory contains a Python script for parsing addresses. It is built around the [`usaddress-scourgify`](https://github.com/GreenBuildingRegistry/usaddress-scourgify) library, with additional functionality added to allow its use from within the broader Java program.

## Setup

In order to be used by the Java script, the code here must first be compiled to a single executable, `address-parser.exe`. This is done as follows:

### Create the Virtual Environment

You must first create a python virtual environment for storing dependencies. Enter `/external/address/venv/`, and then create and activate the virtual environment with the following commands (on Windows):

```
py -m venv venv
.\venv\Scripts\activate
```

```
pip install usaddress-scourgify
pip install pyinstaller
```

### Build the Executable

Next, use PyInstaller to build an executable that Java will run. On Windows, you can use the automatic `build.bat` script, included.

```
.\build.bat
```

(That script is derived from [this documentation](https://pyinstaller.org/en/v5.11.0/usage.html#shortening-the-command) and [this stackoverflow answer](https://stackoverflow.com/a/53529025/10034073)).

### Test the Executable

The resulting executable is located at `./dist/address-parser.exe`. You can test it by providing it some address to parse. For example:

```
.\dist\address-parser.exe "1234 wood st Polisville, NY 98765"
```

It should return the following JSON-encoded string:

```
{"address_line_1": "1234 WOOD ST", "address_line_2": null, "city": "POLISVILLE", "state": "NY", "postal_code": "98765"}
```

If the library encounters an error parsing the address, it will print a JSON object containing three keys:

- `error` - A summary of what happened (failed to parse the address or you didn't give it an address).
- `message` - The actual exception message.
- `stacktrace` - The stacktrace of the error for debugging purposes.

## Parser Documentation

Interacting with the parser is done via the command line. It assumes the following argument structure:

```
.\dist\address.exe [task] <input parameters>
```

The `task` can be one of the following:

- `normalize`
- `normalize_file`
- `compare`
- `compare_file`

Each task requires its own set of parameters.

### __Task: `normalize`__

This parses and normalizes one or more addresses passed via the command line:

```
.\dist\address.exe normalize [address] <optional additional addresses>
```

For example:

```
.\dist\address.exe normalize "1234 Lake blvd, Somewhere NY"
.\dist\address.exe normalize "12 N St city Alaska" "154 West Rd." "7 round st., my town, MI"
```

The program will attempt to parse the given addresses, returning a JSON array containing the addresses as objects. Each object will either be an error message, or it will contain the key `normalized` mapped to the normalized address string. The addresses are listed in the same order they are given in the input.

The second example above will yield the following:

```
[{"normalized": "12 N\nST CITY ALASKA"}, {"normalized": "154 WEST RD"}, {"normalized": "7 ROUND ST\nMY TOWN MI"}]
```

### __Task: `normalize_file`__

This works similarly to [`normalize`](#task-normalize), except that the addresses are stored in a file. This allows a large number of addresses to be normalized, bypassing the character limit of the Windows command line.

Use the following syntax:

```
.\dist\address.exe normalize_file [file_path]
```

The file must contain a JSON array, where each element is an address string to normalize.

The output is a JSON object with key `"output_file"` pointing to the path of the output data. (If a fatal error occurred, the output will be the error message instead.) The output file is located in the same directory as the input, having the same file name but with the suffix `"_normalized"`. The output is also a JSON array, this time containing objects. Each object will either have the key `normalized` mapped to the normalized address, or it will contain an error message.

### __Task: `compare`__

This compares two different addresses to see if they're the same. Use it as follows:

```
.\dist\address.exe compare [address 1] [address 2]
```

For example:

```
.\dist\address.exe compare "105 Round StNowhere North Carolina" "105 round st nowhere nc 12345"
```

This will return a single JSON object. If an error occurred, it will contain the error message. Otherwise, it will contain three keys: `match`, `preference`, and `info`.

`match` maps to a string, either `"EXACT"`, `"INDICATOR"`, or `"NONE"`. An exact match means that the strings are identical. An indicator match means that the strings semantically mean the same thing, but due to different formats or typos, they appear different. None means that the addresses seem entirely unrelated.

`preference` maps to a properly normalized address. In the event where the addresses match, this is useful for determining which one contains more complete information.

`info` is an optional attribute that may map to a string with additional information about the comparison. This is likely only relevant for errors and debugging.

The above example produces the following output:

```
{"match": "INDICATOR", "preference": "105 ROUND ST\nNOWHERE NC 12345", "info": "Fixed malformed spacing"}
```

### __Task: `compare_file`__

Similar to [`compare`](#task-compare), except that the addresses are stored in a file. This allows a large number of addresses to be compared to one particular address, bypassing the character limit of the Windows command line.

Use the following syntax:

```
.\dist\address.exe compare_file [address] [file_path]
```

This will compare the `address` to every address in the file. The file must contain a JSON array, where each element is an address string to compare.

The output is a JSON object with key `"output_file"` pointing to the path of the output data. (If a fatal error occurred, the output will be the error message instead.) The output file is located in the same directory as the input, having the same file name but with the suffix `"_compared"`. The output is also a JSON array, this time containing objects. Each object will either be an error message, or it will contain the keys `"match"`, `"preference"`, and `"info"` (see [`compare`](#task-compare)).

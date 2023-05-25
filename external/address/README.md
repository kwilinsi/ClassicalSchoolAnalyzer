# Python Address Parser

This directory contains a Python script for parsing addresses. It is built around the [`usaddress-scourgify`](https://github.com/GreenBuildingRegistry/usaddress-scourgify) library, with additional functionality added to allow its use from within the broader Java program.

## Create the Virtual Environment

To use the code here, you must first create a virtual environment. Enter `/external/address/venv/`, and then create and enter the virtual environment with the following commands (on Windows):

```
py -m venv venv
.\venv\Scripts\activate
```

```
pip install usaddress-scourgify
pip install pyinstaller
```

## Build the Executable

Next, use PyInstaller to build an executable that Java will run. On Windows, you can use the automatic `build.bat` script, included.

```
.\build.bat
```

(That script is derived from [this documentation](https://pyinstaller.org/en/v5.11.0/usage.html#shortening-the-command) and [this stackoverflow answer](https://stackoverflow.com/a/53529025/10034073)).

## Test the Executable

The resulting executable is located at `./dist/address-parser.exe.` You can test it by providing it some address to parse. For example:

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

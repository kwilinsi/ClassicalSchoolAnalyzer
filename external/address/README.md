# Python Address Parser

This directory contains a Python script for parsing addresses. It is built around the [`usaddress-scourgify`](https://github.com/GreenBuildingRegistry/usaddress-scourgify) library, with additional functionality added to allow its use from within the broader Java program.

## Table of Contents

- [Python Address Parser](#python-address-parser)
  - [Table of Contents](#table-of-contents)
  - [Setup](#setup)
    - [Create the Virtual Environment](#create-the-virtual-environment)
    - [Build the Executable](#build-the-executable)
    - [Test the Executable](#test-the-executable)
  - [Parser Documentation](#parser-documentation)
    - [__Task: `normalize`__](#task-normalize)
    - [__Task: `normalize_file`__](#task-normalize_file)
    - [__Task: `compare`__](#task-compare)
    - [__Task: `compare_file`__](#task-compare_file)
    - [__Task: `normalize_city`__](#task-normalize_city)
    - [__Task: `normalize_city_file`__](#task-normalize_city_file)
    - [__Task: `normalize_state`__](#task-normalize_state)
    - [__Task: `normalize_state_file`__](#task-normalize_state_file)

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
.\dist\address.exe normalize "1234 wood st Polisville, NY 98765"
```

It should return the following JSON-encoded string:

```
[{"address_line_1": "1234 WOOD ST", "address_line_2": null, "city": "POLISVILLE", "state": "NY", "postal_code": "98765", "normalized": "1234 WOOD ST\nPOLISVILLE NY 98765"}]
```

If the library encounters an error parsing the address, it will print a JSON object containing three keys:

- `"error"` — A summary of what happened (failed to parse the address or you didn't give it an address).
- `"message"` — The actual exception message.
- `"stacktrace"` — The stacktrace of the error for debugging purposes.

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
- `normalize_city`
- `normalize_city_file`
- `normalize_state`
- `normalize_state_file`

Each task requires its own set of parameters.

---

### __Task: `normalize`__

This parses and normalizes one or more addresses passed via the command line.

Use the following syntax:

```
.\dist\address.exe normalize [address] <optional additional addresses>
```

The program will attempt to parse the given addresses, outputting a JSON array containing the addresses as objects. Each object will contain the following keys. Note that the addresses are listed in the same order they are given in the input.

- `"address_line_1"` — The first line of the parsed address.
- `"address_line_2"` — The second line of the parsed address.
- `"city"` — The city.
- `"state"` — The state.
- `"postal_code"` — The postal (zip) code.
- `"normalized"` — The complete, normalized address string.
- `"error"` — (Opt) An error message, if the address couldn't be parsed. Normally, this is not present, but if it is, the other keys will be `null`.

**Example 1:**

```
.\dist\address.exe normalize "1234 Lake blvd, Somewhere NY"
```

<details><summary>Output</summary><p>

*The JSON is formatted here for easier viewing.*

```
[
  {
    "address_line_1": "1234 LAKE BLVD",
    "address_line_2": null,
    "city": "SOMEWHERE",
    "state": "NY",
    "postal_code": null,
    "normalized": "1234 LAKE BLVD\nSOMEWHERE NY"
  }
]
```
</p></details>

**Example 1:**

```
.\dist\address.exe normalize "12 N St city Alaska" "154 West Rd." "7 round st., my town, MI"
```

<details><summary>Output</summary><p>

*The JSON is formatted here for easier viewing.*

```
[
  {
    "address_line_1": "12 N",
    "address_line_2": null,
    "city": "SAINT CITY ALASKA",
    "state": null,
    "postal_code": null,
    "normalized": "12 N\nSAINT CITY ALASKA"
  },
  {
    "address_line_1": "154 WEST RD",
    "address_line_2": null,
    "city": null,
    "state": null,
    "postal_code": null,
    "normalized": "154 WEST RD"
  },
  {
    "address_line_1": "7 ROUND ST",
    "address_line_2": null,
    "city": "MY TOWN",
    "state": "MI",
    "postal_code": null,
    "normalized": "7 ROUND ST\nMY TOWN MI"
  }
]
```
</p></details>

---

### __Task: `normalize_file`__

This works similarly to [`normalize`](#task-normalize), except that the addresses are stored in a file. This allows a large number of addresses to be normalized, bypassing the character limit of the Windows command line.

Use the following syntax:

```
.\dist\address.exe normalize_file [file_path]
```

The file must contain a JSON array, where each element is an address string to normalize.

The output is a JSON object with key `"output_file"` pointing to the path of the output data. (If a fatal error occurred, the output will be the error message instead.) The output file is located in the same directory as the input, having the same file name but with the suffix `"_normalized"`.

The file will contain a JSON array of objects. Each object will contain the keys as described under [`normalize`](#task-normalize).

---

### __Task: `compare`__

This compares two different addresses to see if they're the same.

Use the following syntax:

```
.\dist\address.exe compare [address 1] [address 2]
```

This will return a single JSON object containing the following keys:

- `"match"` — A string, either `"EXACT"`, `"INDICATOR"`, or `"NONE"`. An exact match means that the strings are identical. An indicator match means that the strings semantically mean the same thing, but due to different formats or typos, they appear different. None means that the addresses seem entirely unrelated.
- `"address_line_1"`
- `"address_line_2"`
- `"city"`
- `"state"`
- `"postal_code"`
- `"normalized"` — These all come from the [normalization](#task-normalize) process. They are the parsed and normalized values of the *preferred* address. That is, in the event the addresses match (or sometimes even when they don't, like when one is `null`), this contains the preferred form of the address with the best information.
- `"info"` — This optional value may be `null`, or it may be a string with additional information about the comparison. If the parsing failed for one or both of the addresses, this will contain error messages.

**Example:**

```
.\dist\address.exe compare "105 Round StNowhere North Carolina" "105 round st nowhere nc 12345"
```

<details><summary>Output</summary><p>

*The JSON is formatted here for easier viewing.*

```
{
  "match": "INDICATOR",
  "address_line_1": "105 ROUND ST",
  "address_line_2": null,
  "city": "NOWHERE",
  "state": "NC",
  "postal_code": "12345",
  "normalized": "105 ROUND ST\nNOWHERE NC 12345",
  "info": "Malformed spacing"
}
```
</p></details>

---

### __Task: `compare_file`__

This is similar to [`compare`](#task-compare), except that the addresses are stored in a file. This allows a large number of addresses to be compared to one particular address, bypassing the character limit of the Windows command line.

Use the following syntax:

```
.\dist\address.exe compare_file [address] [file_path]
```

This will compare the `address` to every address in the file. The file must contain a JSON array, where each element is an address string to compare.

The output is a JSON object with key `"output_file"` pointing to the path of the output data. (If a fatal error occurred, the output will be the error message instead.) The output file is located in the same directory as the input, having the same file name but with the suffix `"_compared"`.

The file will contain a JSON array of objects. Each object will contain the keys described under [`compare`](#task-compare).

---

### __Task: `normalize_city`__

Normalize a single city passed via the command line. This is done using both a city string to normalize and an address, such that the two can be cross referenced.

Use the following syntax:

```
.\dist\address.exe normalize_city [city] [address]
```

The program will attempt to normalize the city, and it will return a JSON object containing the following keys:

- `"normalized"` — The normalized city name.
- `"address"` — The normalized adddress (for reference/debugging purposes).
- `"address_value"` — The city name as extracted from the address.
- `"error"` — (Opt) An error message for if the address couldn't be parsed. Normally, this is not present.

**Example:**

```
.\dist\address.exe normalize_city "St.  louis" "458 lake rd saint louis mo"
```

<details><summary>Output</summary><p>

*The JSON is formatted here for easier viewing.*

```
{
  "normalized": "SAINT LOUIS",
  "address": "458 LAKE RD\nSAINT LOUIS MO",
  "address_value": "SAINT LOUIS"
}
```
</p></details>

---

### __Task: `normalize_city_file`__

This runs the [`normalize_city`](#task-normalize_city) process on many cities in a file.

Use the following syntax:

```
.\dist\address.exe normalize_city_file [file_path]
```

The file must contain a single JSON array. That array should consist of objects, each containing two keys: `"city"` and `"address"`. The city should be the raw city to normalize, and the address is an optional address for assisting the city normalization.

The output is a JSON object with key `"output_file"` pointing to the path of the output data. (If a fatal error occurred, the output will be the error message instead.) The output file is located in the same directory as the input, having the same file name but with the suffix `"_normalized"`.

The file will contain a JSON array of objects. Each object will contain the keys described under [`normalize_city`](#task-normalize_city).

---

### __Task: `normalize_state`__

Normalize a single state passed via the command line, along with the help an address.

Use the following syntax:

```
.\dist\address.exe normalize_state [state] [address]
```

This behaves pretty much identically to [`normalize_city`](#task-normalize_city), having the same set of keys in the JSON output. Of course, the `"normalized"` value will contain a state name, rather than a city name.

**Example:**

```
.\dist\address.exe normalize_state "cal" "12 hill st San Francisco california"
```

<details><summary>Output</summary><p>

*The JSON is formatted here for easier viewing.*

```
{
  "normalized": "CA",
  "address": "12 HILL ST\nSAN FRANCISCO CA",
  "address_value": "CA"
}
```
</p></details>

---

### __Task: `normalize_state_file`__

Run the [`normalize_state`](#task-normalize_state) process on multiple states from a file.

Use the following syntax:

```
.\dist\address.exe normalize_state_file [file_path]
```

This behaves pretty much identically to [`normalize_city_file`](#task-normalize_city_file), having the same set of keys in the JSON output.

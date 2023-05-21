# Database

This program uses both a MySQL database and a standard directory tree for storing data. The tables in the database are
as follows (ordered here semantically-sensibly):

### Organizations


### Districts


### Schools


### DistrictOrganizations


### Cache

This stores file paths from the standard directory tree. Its primary use is quickly retrieving the html content of
websites without connecting to their server to retrieve the content every time. This is especially relavent for
debugging purposes.
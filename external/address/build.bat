pyinstaller ^
    --onefile ^
    --hidden-import=pycrfsuite._dumpparser ^
    --hidden-import=pycrfsuite._logparser ^
    --add-data "venv/Lib/site-packages/usaddress/usaddr.crfsuite;usaddress" ^
    address.py

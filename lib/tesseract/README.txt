Text recognition (OCR) libraries and trained data.

win32-x86-64/libtesseract541.dll   Tesseract 5.4.1, taken from the Tess4J 5.13.0 jar (Apache License 2.0, see LICENSE-tesseract)
win32-x86-64/libleptonica1841.dll  Leptonica 1.84.1, taken from the Lept4J 1.19.1 jar (see LICENSE-leptonica)
tessdata/deu.traineddata           German model from https://github.com/tesseract-ocr/tessdata_fast (Apache License 2.0)
tessdata/eng.traineddata           English model from https://github.com/tesseract-ocr/tessdata_fast (Apache License 2.0)

The DLLs need the Microsoft Visual C++ 2015-2022 Redistributable (x64).

linux-x86-64/ (not in version control) holds the Linux libraries used for development, taken from the
tesserocr wheel on PyPI (libtesseract 5.5.1 with its dependencies); see OcrEngine.loadLibrary for the file names.
Without them, OcrEngine falls back to a system-wide libtesseract.

The libraries are accessed through JNA (see net.sourceforge.docfetcher.model.ocr.OcrEngine); no Tess4J code is used.

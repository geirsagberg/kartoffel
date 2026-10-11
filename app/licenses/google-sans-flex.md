# Google Sans Flex

The bundled font comes from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/googlesansflex)
and uses the [SIL Open Font License](google-sans-flex-OFL.txt).

Only the weight axis varies, from 400 to 600, covering Normal, Medium, and SemiBold.
Optical size (18), width (100), roundness (0), and slant (0) stay at the original defaults.
All character mappings are retained. The asset is 243,440 bytes.

To regenerate from the original Google Fonts TTF with fontTools 4.66.1:

```sh
fonttools varLib.instancer original.ttf \
  wght=400:600 opsz=drop wdth=drop ROND=drop slnt=drop \
  --output=app/src/main/res/font/google_sans_flex.ttf
```

The [fontTools instancer](https://fonttools.readthedocs.io/en/latest/varLib/instancer.html)
pins unused axes and restricts the retained weight range.

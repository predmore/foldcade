# Licensing

This page says, in plain language, which license covers what. It is not legal advice.

## Themes

A theme is data. The zip, its `theme.json`, and the images and sounds it names are not program code. A theme can use any license.

A theme that ships in this repository keeps the license written next to those files. Afterglow's art is CC BY-SA 4.0, and its font is the SIL Open Font License. See [credits](../licenses/CREDITS.md).

## External plugins

A plugin that is not in this repository, and that talks to Foldcade only through the published plugin API, can use any license.

That permission is the Foldcade Plugin Exception, a [GPLv3 section 7](../LICENSE) additional permission, at the top of the main license.

## Bundled code

Code in this repository is GNU GPLv3, except where a file says otherwise. Plugins and samples that ship here are GPLv3, including the sample that shows the out-of-tree shape.

## Plugin API

The published plugin API is the `:api` module. It is Apache License 2.0. The license text is [api/LICENSE](../api/LICENSE).

The published theme format is the theme zip described above. Writing a theme against that format does not make the theme GPLv3. The code in this repository that reads a theme stays GPLv3.

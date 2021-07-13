# fabric.mod.json

In all cases, the mod JSON, `fabric.mod.json`, is defined as either an object containing a "schemaVersion" key with an integer value, denoting the version of the format.

If a "schemaVersion" key is missing, assume version "0".

It is important to remember that the Fabric mod JSON is **authoritative**. This means that Loader relies on it specifically to gather the necessary information - from the perspective of an external tool, its contents can be trusted and relied upon.

## Version 1 (current)

**Pertains to Loader 0.4.0 and above.**

### Types

#### ContactInformation

A string->string dictionary containing contact information.

The following keys are officially defined:

- **email**: Contact e-mail pertaining to the mod. Must be a valid e-mail address.
- **irc**: IRC channel pertaining to the mod. Must be of a valid URL format - for example: `irc://irc.esper.net:6667/charset` for #charset at EsperNet - the port is optional, and assumed to be 6667 if not present.
- **homepage**: Project or user homepage. Must be a valid HTTP/HTTPS address.
- **issues**: Project issue tracker. Must be a valid HTTP/HTTPS address.
- **sources**: Project source code repository. Must be a valid URL - it can, however, be a specialized URL for a given VCS (such as Git or Mercurial).

There are no mandatory keys.

The list is not exhaustive - mods may provide additional, non-standard keys (such as **discord**, **slack**, **twitter**) - if possible, they should be valid URLs.

#### EntrypointContainer

An EntrypointContainer is an object.

The keys match the getEntrypoints() "type" field, and are the type of the entrypoints to be listed - "main", "client", "server". The values of those keys are arrays, containing either strings (of the object key "value"'s value) or objects of the following form:

- "adapter": Optional key denoting the language adapter to use. If empty, assume "default".
- "value": The default language adapter uses this specific key as a string value of any of the following formats:
  - "my.package.MyClass", which points to a class to be instantiated,
  - "my.package.MyClass::thing", which points to a static field (contents returned) or method handle (for interface types, proxied automatically) named "thing".

Fabric, by default, will run all "main" entrypoints (type ModInitializer), and all "client" (type ClientModInitializer) or "server" (type DedicatedServerModInitializer) entrypoints after that.

#### NestedJarEntry

An object with the following keys, of which only "file" is mandatory:

- "file" - a string value pointing to a path from the root of the mod to a nested JAR which should be loaded alongside the outer mod JAR.

#### Person

Can be in one of two forms:

- a string (assumed to resolve to a "name" key in the object),
- an object.

In the case of an object, the following keys are defined:

- **name**: The real name, or username, of the person. Mandatory.
- **contact** An optional ContactInformation object containing contact information pertaining to the person.

#### VersionRange

A string or array of string declaring supported version ranges. In the case of an array, an "OR" relationship is assumed - that is, only one range has to match for the collective range to be satisfied.

In the case of all versions, `*` is a special string declaring that any version is matched by the range. In addition, exact string matches must be possible regardless of the version type.

For semantic versions, the specification follows a rough subset of the [NPM semver](https://docs.npmjs.com/misc/semver) specification, in particular the following features are supported:

- `=` as a leading character, or the lack of one, denoting an exact match,
- Version ranges - a set of space-delimited comparators of the `>=`, `>`, `<=`, `<`, `=` or no prefix, following NPM behaviour and declaring an intersection of supported versions within the scope of the string,
- X-Ranges,
- Tilde Ranges,
- Caret Ranges.

### Mandatory fields

- **id**: Contains the mod identifier - a string value matching the `^[a-z][a-z0-9-_]{1,63}$` pattern.
- **version**: Contains the mod version - a string value, optionally matching the [Semantic Versioning 2.0.0](https://semver.org/) specification.

### Optional fields (mod loading)

- **environment**: For games with multiple environments - is a string value (or an array of string values) defining the environments the mod should be considered for loading on. Supported values are:
  - "*" - all environments (default),
  - "client" - the game client,
  - "server" - the game dedicated server (integrated servers are not included here).
- **entrypoints**: Contains an EntrypointContainer. If not present, assume empty object.
- **jars**: Contains an array of NestedJarEntry objects. If not present, assume empty object.
- **languageAdapters**: A string->string dictionary, connecting namespaces to LanguageAdapter implementations.
- **mixins**: Contains a list of mixin configuration files for the Mixin library as filenames relative to the mod root - an array of (can be mixed):
  - string values,
  - objects containing a "config" key (filename), as well as optional keys of the following types: "environment".
- **accessWidener**: A file path to an access widener relative to the mod root. If not present, assume the mod has no access widener.

### Optional fields (dependency resolution)

All of the following keys follow the format of a string->VersionRange dictionary, where the string key matches the desired ID.

- **depends**: for these dependencies, a failure to match causes a hard failure,
  - **recommends**: for these dependencies, a failure to match causes a soft failure (warning),
  - **suggests**: these dependencies are not matched and are primarily used as metadata,
  - **conflicts**: for these dependencies, a successful match causes a soft failure (warning),
  - **breaks**: for these dependencies, a successful match causes a hard failure.

### Optional fields (metadata)

- **name**: Contains the user-facing mod name - a string value. If not present, assume it matches **id**.
- **description**: Contains the user-facing mod description - a string value. If not present, assume empty string.
- **authors**: Contains the direct authorship information - an array of Person values.
- **contributors**: Contains the contributor information - an array of Person values.
- **contact**: Contains the contact information for the project - a ContactInformation object.
- **license**: Contains the licensing information - a string value, or an array of string values.
  - This should provide the complete set of preferred licenses conveying the entire mod package. In other words, compliance with all listed licenses should be sufficient for usage, redistribution, etc. of the mod package as a whole.
  - For cases where a part of code is dual-licensed, choose the preferred license. The list is not exhaustive, serves primarily as a kind of hint, and does not prevent you from granting additional rights/licenses on a case-by-case basis.
  - To aid automated tools, it is recommended to use [SPDX License Identifiers](https://spdx.org/licenses/) for "open source" licenses.
- **icon**: Contains the mod's icon, as a square .PNG file. (Minecraft resource packs use 128x128, but that is not a hard requirement - a power of two is, however, recommended.) Can be provided in one of two forms:
  - A string, providing the path (from the mod's root) to a single .PNG file,
  - A string->string dictionary, where the keys conform to widths of each PNG file, and the values are said files' paths.

### Custom fields

A **custom** field can be provided in the JSON. It is a dictionary of string keys to JSON elements, and the contents of said JSON elements are entirely arbitrary. The Fabric loader will make no effort to read its contents, aside from a situation in which custom features in older schema versions become official in future schema versions, as a compatibility measure - those will be adequately documented.

It is recommended that the keys be namespaced with the ID of the mod relying on them (if such a mod exists) to prevent conflicts.

## Version 0

This version is utilized if the "schemaVersion" field is missing - where it is assumed to be 0\. It was used by all Fabric Loader versions prior to 0.4.0\. It was inspired by the [craftson/spec](https://github.com/craftson/spec) specification.

_TODO: Document me!_

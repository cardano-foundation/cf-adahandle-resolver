# 12. JavaScript plugins, not Python

- Status: Accepted
- Date: 2026-11-04

## Context

The resolver comes with Yaci Store's plugin system: scripts, configured in YAML, that react to chain events or run on a
schedule, for example a webhook when an ADA Handle is minted or burned. Many people who run the resolver aren't Java
developers, so plugins are a way to extend it without writing Java or rebuilding anything.

Yaci Store supports four plugin languages:

- **MVEL** and **SpEL**, built in;
- **JavaScript** and **Python**, through the optional `yaci-store-plugin-polyglot` module, which runs them on GraalVM's
  polyglot engines (GraalJS and GraalPy).

Until now the resolver didn't include that module, so only MVEL and SpEL worked. MVEL is Java-like and little known;
JavaScript is the language most operators can already write.

Measured on the resolver's jar (98 MB without the module):

| Added | Jar size | Grows by |
|---|---|---|
| JavaScript (GraalJS, community edition) | 162 MB | +64 MB |
| Python (GraalPy, community edition) as well | 323 MB | +161 MB more |

## Decision

Include JavaScript plugins, and leave Python out for now.

- `yaci-store-plugin-polyglot` is added with Python excluded, and with GraalJS's community edition (`js-community`,
  Universal Permissive License).
- It is in the `js-plugins` Maven profile, on by default. The native image is built with `-DskipJsPlugins`: it is
  built and tested without GraalJS, and keeps MVEL and SpEL plugins only.
- The jar's manifest allows native access (`Enable-Native-Access: ALL-UNNAMED`), and the resolver turns off GraalJS's
  warning about running in interpreter mode, which it does on a standard JDK. Short plugin scripts don't need the
  optimizing compiler.

Python is not left out for technical reasons: adding it is another dependency, the same way, and a larger image. If you
need Python plugins, open an issue on GitHub; with a real use case behind it, we'll add it.

## Consequences

- Operators can write plugins in JavaScript, MVEL or SpEL. Scripts reach event fields through Java getters, such as
  `event.getTxMintBurns()`, because events are Java objects.
- The JVM jar and Docker image grow by about 64 MB. Nothing extra is loaded unless plugins are switched on
  (`store.plugins.enabled`).
- JavaScript plugins are not available in the native image.
- The GraalJS version (`graalvm.polyglot.version` in `pom.xml`) has to stay equal to the polyglot version that
  `yaci-store-plugin-polyglot` uses; check it when Yaci Store is updated.
- TypeScript isn't supported directly; compile it to JavaScript and point the plugin at the `.js` file.

## References

- `JsPluginTest`: a JavaScript event handler and scheduler running in the resolver.
- Tested against mainnet: a JavaScript `MintBurnEvent` handler posted handle mints to a webhook, and a JavaScript
  scheduler queried `ada_handle`.
- Yaci Store: `components/plugin-polyglot` (`PluginExtraAutoConfig`, `JsPolyglotPluginFactory`); plugin docs at
  https://store.yaci.xyz/plugins/plugin-getting-started.

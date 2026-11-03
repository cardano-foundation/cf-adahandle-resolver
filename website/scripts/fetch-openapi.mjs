// Refreshes openapi/adahandle-resolver.json from a running resolver.
//
//   SPRING_ACTIVE_PROFILES=disable-indexer,h2 DB_URL=jdbc:h2:mem:spec java -jar target/cf-adahandle-resolver-*.jar
//   npm run openapi:fetch                       # or: npm run openapi:fetch -- http://host:9095
//
// The spec is committed so the site builds without a running backend.
import { writeFile } from 'node:fs/promises';

const baseUrl = process.argv[2] ?? 'http://localhost:9095';
const output = new URL('../openapi/adahandle-resolver.json', import.meta.url);

// Resolver endpoints first; Yaci Store's built-in endpoints after.
const tagOrder = ['Addresses', 'ADA Handles', 'Network API', 'Plugin API'];

const response = await fetch(`${baseUrl}/apidocs`);
if (!response.ok) {
	throw new Error(`GET ${baseUrl}/apidocs failed: ${response.status} ${response.statusText}`);
}
const spec = await response.json();

// springdoc reports the URL it was fetched from; publish the default local one instead.
spec.servers = [{ url: 'http://localhost:9095', description: 'Local resolver (default port)' }];

const rank = (name) => {
	const index = tagOrder.indexOf(name);
	return index === -1 ? tagOrder.length : index;
};
spec.tags = [...(spec.tags ?? [])].sort((a, b) => rank(a.name) - rank(b.name));
spec.paths = Object.fromEntries(
	Object.entries(spec.paths).sort(
		([, a], [, b]) => rank(Object.values(a)[0]?.tags?.[0]) - rank(Object.values(b)[0]?.tags?.[0]),
	),
);

await writeFile(output, `${JSON.stringify(spec, null, '\t')}\n`);
console.log(`Wrote ${output.pathname} (${Object.keys(spec.paths).length} paths, version ${spec.info.version})`);

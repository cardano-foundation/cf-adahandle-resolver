// Refreshes openapi/adahandle-resolver.json from a running resolver.
//
//   SPRING_ACTIVE_PROFILES=disable-indexer,h2 DB_URL=jdbc:h2:mem:spec java -jar target/cf-adahandle-resolver-*.jar
//   npm run openapi:fetch -- http://localhost:9095 1.0.0
//
// The second argument (or API_VERSION) is the release the site documents. The resolver reports the version it was
// built as, which on develop is a -SNAPSHOT; the site must show a release, so a snapshot without one is refused.
//
// The spec is committed so the site builds without a running backend.
import { writeFile } from 'node:fs/promises';

const baseUrl = process.argv[2] ?? 'http://localhost:9095';
const output = new URL('../openapi/adahandle-resolver.json', import.meta.url);

// The spec has only the resolver's endpoints (springdoc.packages-to-scan); addresses first.
const tagOrder = ['Addresses', 'ADA Handles'];

const response = await fetch(`${baseUrl}/apidocs`);
if (!response.ok) {
	throw new Error(`GET ${baseUrl}/apidocs failed: ${response.status} ${response.statusText}`);
}
const spec = await response.json();

const version = process.argv[3] ?? process.env.API_VERSION;
if (version) {
	spec.info.version = version;
}
if (/SNAPSHOT/i.test(spec.info.version)) {
	throw new Error(
		`The resolver reports version ${spec.info.version}. Pass the release the site documents: ` +
			'npm run openapi:fetch -- http://localhost:9095 1.0.0',
	);
}

// springdoc reports the URL it was fetched from; publish the public mainnet instance and the default local one instead.
spec.servers = [
	{ url: 'https://adahandle.mainnet.cardanofoundation.org', description: 'Public mainnet instance (Cardano Foundation)' },
	{ url: 'http://localhost:9095', description: 'Your own resolver (default port)' },
];

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

// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightOpenAPI, { openAPISidebarGroups } from 'starlight-openapi';
import tailwindcss from '@tailwindcss/vite';

// Served from GitHub Pages next to the existing coverage report and badges. Themed like the Cardano Foundation's other
// Starlight sites (cf-token-metadata-registry): src/styles/starlight.css for the docs, src/pages/index.astro for the home page.
export default defineConfig({
	site: 'https://cardano-foundation.github.io',
	base: '/cf-adahandle-resolver',
	integrations: [
		starlight({
			title: 'ADA Handle Resolver',
			description:
				'A scoped Cardano indexer by the Cardano Foundation that resolves ADA Handles to addresses, and back, over REST.',
			customCss: ['./src/styles/starlight.css'],
			components: {
				Footer: './src/components/Footer.astro',
				SiteTitle: './src/components/SiteTitle.astro',
			},
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/cardano-foundation/cf-adahandle-resolver',
				},
				{ icon: 'discord', label: 'Discord', href: 'https://discord.gg/4WVNHgQ7bP' },
			],
			editLink: {
				baseUrl: 'https://github.com/cardano-foundation/cf-adahandle-resolver/edit/main/website/',
			},
			lastUpdated: true,
			plugins: [
				// Generated from the spec the resolver serves at /apidocs; refresh with `npm run openapi:fetch`.
				starlightOpenAPI([
					{
						base: 'api',
						schema: './openapi/adahandle-resolver.json',
						sidebar: { label: 'OpenAPI', collapsed: false, operations: { badges: true } },
					},
				]),
			],
			sidebar: [
				{
					label: 'Start here',
					items: [
						{ label: 'Introduction', slug: 'getting-started/introduction' },
						{ label: 'Quickstart', slug: 'getting-started/quickstart' },
					],
				},
				{
					label: 'Guides',
					items: [
						{ label: 'Configuration', slug: 'guides/configuration' },
						{ label: 'Deployment', slug: 'guides/deployment' },
						{ label: 'Scaling the API', slug: 'guides/scaling' },
						{ label: 'Monitoring', slug: 'guides/monitoring' },
						{ label: 'Contributing', slug: 'guides/contributing' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'REST API', slug: 'reference/api' },
						{ label: 'How indexing works', slug: 'reference/architecture' },
					],
				},
				...openAPISidebarGroups,
			],
		}),
	],
	vite: {
		plugins: [tailwindcss()],
	},
});

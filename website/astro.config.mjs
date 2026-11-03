// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightOpenAPI, { openAPISidebarGroups } from 'starlight-openapi';

// Served from GitHub Pages next to the existing coverage report and badges.
export default defineConfig({
	site: 'https://cardano-foundation.github.io',
	base: '/cf-adahandle-resolver',
	integrations: [
		starlight({
			title: 'ADA Handle Resolver',
			description:
				'A scoped Cardano indexer by the Cardano Foundation that resolves ADA Handles to addresses, and back, over REST.',
			logo: {
				light: './src/assets/logo-light.svg',
				dark: './src/assets/logo-dark.svg',
				alt: 'ADA Handle Resolver',
			},
			favicon: '/favicon.svg',
			customCss: [
				'@fontsource-variable/syne',
				'@fontsource/chivo/400.css',
				'@fontsource/chivo/600.css',
				'./src/styles/theme.css',
			],
			components: {
				Hero: './src/components/Hero.astro',
				Footer: './src/components/Footer.astro',
				ThemeProvider: './src/components/ThemeProvider.astro',
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
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'REST API', slug: 'reference/api' },
						{ label: 'How indexing works', slug: 'reference/architecture' },
						{ label: 'Design decisions', slug: 'reference/decisions' },
					],
				},
				...openAPISidebarGroups,
			],
		}),
	],
});

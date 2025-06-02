# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This repository contains the documentation website for the Taxi language, built with NextJS and TailwindCSS. The website includes documentation, guides, blog posts, and interactive code examples for Taxi.

## Development Commands

### Setup and Running
```bash
# Install dependencies
npm install

# Run development server (with legacy OpenSSL provider flag for compatibility)
npm run dev

# Build for production
npm run build

# Start production server
npm run start

# Export static site
npm run export

# Format code with Prettier
npm run format

# Generate and aggregate changelog
npm run changelog
```

## Key Architectural Components

### NextJS and MDX
- The site is built with NextJS (v12.3.1) and uses MDX for content authoring
- Documentation and blog posts are written in MDX format
- Custom MDX components enhance the content with interactive elements

### Interactive Code Examples
- `PlaygroundSnippet` component (`src/components/PlaygroundSnippet.tsx`) allows embedding runnable Taxi code examples
- Examples connect to the Taxi Playground API (https://playground.taxilang.org) to execute code
- The component supports editing and running code directly in the documentation

### Custom Components
- `Accordion` component for collapsible content 
- Various components for documentation layout and styling

### Documentation Structure
- Main documentation is in `src/pages/docs/`
- Blog posts are in `src/pages/blog/`
- Changelog is in `src/pages/changelog/`

## File Structure Conventions

### Blog Post Creation
1. Create a directory in `src/pages/blog` with the format `YYYY-MM-DD-title`
2. Add a single `index.mdx` file in that directory
3. Include metadata using ESM format (not traditional frontmatter)
4. Add author information to `src/authors.js`
5. Place images in the blog post directory

### Code Snippets
- Use standard markdown code blocks with language specification
- For multiple tabs, use the `SnippetGroup` component
- Taxi language is supported with custom syntax highlighting

### Documentation Updates
- The stdlib documentation (`src/pages/docs/language/stdlib.mdx`) is automatically generated from the compiler codebase
- Docs include interactive examples with the `PlaygroundSnippet` component

## Working with the Taxi Playground Integration
When using or modifying the `PlaygroundSnippet` component:
1. The component embeds code examples that can be run in the browser
2. It connects to the Taxi Playground API at https://playground.taxilang.org
3. Code examples are compressed and encoded in the URL for sharing
4. Query results are displayed inline after execution

## Important Notes
- This is specifically the documentation site for Taxi lang, not the language implementation itself
- Most updates will focus on improving documentation content and examples
- The site uses a mix of JavaScript and TypeScript files
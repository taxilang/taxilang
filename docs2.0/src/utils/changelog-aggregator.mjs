import fs from 'fs/promises';
import path from 'path';
import matter from 'gray-matter';
import {fileURLToPath} from 'url';


async function changelogAggregator() {
  console.log('Aggregating changelogs')
  const changelogDir = path.join(process.cwd(), 'src/pages/changelog/_versions');
  const outputDir = path.join(process.cwd(), 'src/pages/changelog/releases');

// Ensure the output directory exists
  await fs.mkdir(outputDir, {recursive: true});

// Read all changelog files
  const files = (await fs.readdir(changelogDir)).filter(file => file.endsWith('.md'));

  const groupedNotes = {};

// Process each changelog file
  for (const file of files) {
    const filePath = path.join(changelogDir, file);
    const fileContent = await fs.readFile(filePath, 'utf-8');
    const {data, content} = matter(fileContent);

    const {aggregateVersion, releaseDate} = data;

    // Initialize group if it doesn't exist
    if (!groupedNotes[aggregateVersion]) {
      groupedNotes[aggregateVersion] = {
        notes: [],
        lastUpdated: null,
      };
    }

    // Add the note and update the last updated date
    groupedNotes[aggregateVersion].notes.push(content);
    const releaseDateObj = new Date(releaseDate);
    if (
      !groupedNotes[aggregateVersion].lastUpdated ||
      releaseDateObj > new Date(groupedNotes[aggregateVersion].lastUpdated)
    ) {
      groupedNotes[aggregateVersion].lastUpdated = releaseDate;
    }
  }

// Write the grouped MDX files
  for (const [aggregateVersion, {notes, lastUpdated}] of Object.entries(groupedNotes)) {
    const combinedContent = notes.join('\n\n');
    const outputFilePath = path.join(outputDir, `${aggregateVersion || 'next'}.mdx`);

    const frontmatter = `---
LastUpdated: '${lastUpdated}'
version: '${aggregateVersion}'
title: Release notes - ${aggregateVersion}
---

${combinedContent}
`;

    await fs.writeFile(outputFilePath, frontmatter, 'utf-8');
    console.log(`Generated changelog for version ${aggregateVersion}`);
  }

  console.log('Aggregated changelogs successfully.');
}
const isMainModule = process.argv[1] === fileURLToPath(import.meta.url);
if (isMainModule) {
  changelogAggregator()
    .then(() => console.log('Changelogs generated'))
    .catch(console.error);
}
export default changelogAggregator;

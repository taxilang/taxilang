import conventionalChangelog from 'conventional-changelog';
import {mkdir, writeFile, access} from 'fs/promises';
import {join} from 'path';
import {fileURLToPath} from 'url';
import {Transform} from 'stream';
import path from 'path';

/**
 * This uses conventional changelog to emit individual changelog files.
 * We differ from contentional changelog by:
 *  - Having a seperate file per release
 *  - Adding frontmatter to each
 *
 * The frontmatter includes an aggregate version - which allows other tooling
 * to collate minor + patch versions together.
 * eg: 0.34.mdx would contain 0.34.0, 0.34.1
 *
 * Note - the creation of aggregate files is not part of this function.
 * @param outputDir
 * @returns {Promise<unknown>}
 */
async function generateVersionChangelogs() {
  const outputDir = path.join(process.cwd(), 'src/pages/changelog/_versions');
  await mkdir(outputDir, {recursive: true});

  function getVersionFromContent(content) {
    let version = "next"
    let aggregateVersion = "next"
    if (content.startsWith("## ")) {
      // Trim ## 1.53.0 to 1.53.0
      version = content.substring(3, content.indexOf("\n")).trim()
      // Strip 1.53.0 to just 1.53
      if (version !== "next") {
        aggregateVersion = version.substring(0, version.lastIndexOf("."))
      }

    }
    // Find the release date by looking for something like
    // releaseDate='2024-10-21'
    const releaseDate = content.substr(content.indexOf("\n") + 1, 10)
    return {version, aggregateVersion, releaseDate}
  }


  const processVersions = new Transform({
    transform(chunk, encoding, callback) {
      const content = chunk.toString();

      const {version, releaseDate, aggregateVersion} = getVersionFromContent(content);
      const frontMatter = `---
version: ${version}
aggregateVersion: '${aggregateVersion}'
releaseDate: ${releaseDate}
title: ${aggregateVersion} release notes
---`


      const tidiedContent = modifyChangelogEntry(content, version, releaseDate)
      const contentWithFrontmatter = frontMatter + '\n' + tidiedContent;
      this.push(JSON.stringify({version, content: contentWithFrontmatter}));
      callback();
    }
  });

  return new Promise((resolve, reject) => {
    const writePromises = [];


    processVersions
      .on('data', async (data) => {
        const { version, content } = JSON.parse(data);
        const filePath = path.join(outputDir, `${version}.md`);

        if (version === 'next') {
          // Always write the file if version is 'next'
          writePromises.push(writeFile(filePath, content));
        } else {
          try {
            // Check if the file exists
            await access(filePath);
            // If it exists, do nothing
            console.log(`File ${filePath} already exists. Skipping.`);
          } catch (err) {
            if (err.code === 'ENOENT') {
              // File does not exist, write the file
              writePromises.push(writeFile(filePath, content));
            } else {
              // If it's another error, rethrow it
              throw err;
            }
          }
        }
      })
      .on('end', async () => {
        await Promise.all(writePromises);
        resolve();
      })
      .on('error', reject);

    conventionalChangelog({preset: 'angular', releaseCount: 0}, null, null, null, {
      headerPartial: `## {{#if version}}{{version}}{{/if}}{{#unless version}}next{{/unless}}
{{#if date}}{{date}}{{/if}}`
    })
      .pipe(processVersions);
  });
}

function replaceH1HeadingWithH2(content) {
  return content;
}

function moveReleaseDateAndDiffUrlIntoComponent(content) {
  return content;
}

/**
 * Applies temnplate changes to the default output of conventional changelog.
 * Note - in theory this is possible by changing the handlebar template, but I
 * couldn't find a way to make that work.
 *
 *  - Version entries are always h2 (default is h1 for minor releases)
 *  - We move the compare url and release date off the h2 into an mdx component
 * @param content
 * @returns {undefined}
 */
function modifyChangelogEntry(content, version, releaseDate) {
  let modifiedContent = replaceH1HeadingWithH2(content);
  modifiedContent = moveReleaseDateAndDiffUrlIntoComponent(modifiedContent, version, releaseDate)
  return modifiedContent;
}


const isMainModule = process.argv[1] === fileURLToPath(import.meta.url);
if (isMainModule) {
  generateVersionChangelogs()
    .then(() => console.log('Changelogs generated'))
    .catch(console.error);
}

export default generateVersionChangelogs;

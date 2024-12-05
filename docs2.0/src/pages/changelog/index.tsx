import fs from 'fs';
import path from 'path';
import Link from 'next/link';
import {Prose} from "@/components/docs/Prose";
import matter from "gray-matter";
import {HeadMetaTags} from "@/components/common";
import * as React from "react";

export async function getStaticProps() {
  const changelogDir = path.join(process.cwd(), 'src/pages/changelog/releases');
  const files = fs.readdirSync(changelogDir).filter(file => file.endsWith('.mdx'));


  const versions = files.map(file => {
      const fileContent = fs.readFileSync(path.join(changelogDir, file), 'utf-8');
      const {data, content} = matter(fileContent);
      const updated = data.LastUpdated;
      const version = data.version;
      return {
        updated,
        version,
        filename: path.basename(file, '.mdx')
      }
    }
  );

  return {
    props: {
      versions,
    },
  };
}

export default function ChangelogIndex({versions}) {
  const sortedVersions = versions.concat().sort((a, b) => {
    new Date(Date.parse(a.updated)).getTime() - new Date(Date.parse(b.updated)).getTime()
  });
  return (
    <>
      <HeadMetaTags title="Taxi - Changelog"/>
      <Prose className={'max-w-xl m-auto'}>
        <h2 className={'text-white'}>Changelog</h2>
        <ul>

          {sortedVersions.reverse().map(version => (
            <li key={version}>
              <Link href={`/changelog/releases/${version.filename}`}>{`Version ${version.version}`}</Link>
            </li>
          ))}
        </ul>
      </Prose>
    </>
  );
}

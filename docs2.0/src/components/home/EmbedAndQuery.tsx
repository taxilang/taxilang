import {ChevronRightIcon} from "@heroicons/react/24/solid";
import * as React from "react";
import {useState} from "react";
import OpenApiIcon from "./open-api-icon";
import ProtobufIcon from "./protobuf-icon";
import {microservicesCodeSnippets, publishYourApiCodeSnippets} from "./code-snippets";
import {Tabs} from "@/components/Tabs";
import {Snippet} from "@/components/Steps";
import {TaxiLogo} from "@/components/icons/taxi-icon-yellow";
import {BigText, SectionHeading, SectionHeadingParagraph} from "@/components/common";
import {LinkButton} from "@/components/LinkButton";


export const EmbedAndQuery = ({
                                publishYourApiHighlightedSnippets,
                                microservicesCodeSnippets
                              }) => {
  return (
    <div>
      <TagYourApis highlightedSnippets={publishYourApiHighlightedSnippets}/>
      <Query highlightedSnippets={microservicesCodeSnippets}/>
    </div>
  )
}

const TagYourApis = (highlightedCode) => {

  let languageTabs = {
    'Open API': OpenApiIcon,
    'Protobuf': ProtobufIcon,
    'Database': (selected) => (
      <>
        <svg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' strokeWidth={1.5} stroke='currentColor'
             className='w-6 h-6'>
          <path strokeLinecap='round' strokeLinejoin='round'
                d='M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 0v3.75m-16.5-3.75v3.75m16.5 0v3.75C20.25 16.153 16.556 18 12 18s-8.25-1.847-8.25-4.125v-3.75m16.5 0c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125'/>
        </svg>

      </>
    ),
    'Taxi': () => (<TaxiLogo width={48} height={48} useCurrentColor={true}/>)
  };
  const tabCodeSnippets = {
    'Open API': {
      code: publishYourApiCodeSnippets['open-api-example'],
      highlightedCode: highlightedCode.highlightedSnippets['open-api-example']
    },
    'Protobuf': {
      code: publishYourApiCodeSnippets['protobuf-example'],
      highlightedCode: highlightedCode.highlightedSnippets['protobuf-example']
    },
    'Taxi': {
      code: publishYourApiCodeSnippets['taxi'],
      highlightedCode: highlightedCode.highlightedSnippets['taxi']
    },
    'Database': {
      code: publishYourApiCodeSnippets['database-example'],
      highlightedCode: highlightedCode.highlightedSnippets['database-example']
    }
  };

  const [tab, setTab] = useState('Open API');

  return (<div className='max-w-2xl mx-auto min-h-[590px]'>
    <div className='flex flex-col items-center py-8'>
      <div className={'text-center text-lg pb-8'}>
        <SectionHeading>Describe</SectionHeading>
        <BigText>Works with your existing stack</BigText>
        <SectionHeadingParagraph>
          <p>Taxi works with your existing API schemas and specs - simply embed tags to show how data relates.</p>
          <p>Alternatively, use Taxi to describe your APIs, CSV files, Event payloads and more</p>
        </SectionHeadingParagraph>
      </div>

      <Tabs
        tabs={languageTabs}
        selected={tab}
        onChange={(tab) => setTab(tab)}
        className='text-yellow-400'
        iconClassName='yellow-400'
      />
    </div>
    <div>
      <Snippet highlightedCode={tabCodeSnippets[tab].highlightedCode} code={tabCodeSnippets[tab].code}/>
    </div>
  </div>)
}

const Query = (highlightedCode) => {
  return (<div className='max-w-5xl mx-auto py-16'>
      <div className={'text-center text-lg'}>
        <SectionHeading>Orchestrate & Integrate</SectionHeading>
        <BigText>Integrate everything</BigText>
        <SectionHeadingParagraph>
          <p>Use the same tags you embedded in your API specs to write queries for data - <br />across APIs, databases, Kafka topics, S3 buckets, the lot.</p>
          <p>There's no resolvers or glue code to maintain, API clients to generate, or YAML mapping files misalign.</p>
        </SectionHeadingParagraph>
      </div>
    <div className={'max-w-2xl mx-auto'}>
      <Snippet highlightedCode={highlightedCode.highlightedSnippets['taxi-query']}
               code={microservicesCodeSnippets['taxi-query']}/>
      <div className='w-full flex flex-col items-center my-8'>
        <LinkButton styles="hidden md:flex" link='https://playground.taxilang.org/' label='Try the playground'/>
      </div>
    </div>
  </div>)
}

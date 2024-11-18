import {Footer} from '@/components/Footer';
import GetStartedButton from '@/components/GetStartedButton';
import {HeadMetaTags, Paragraph} from '@/components/common';
import {LinkButton} from '@/components/LinkButton';
import * as React from 'react';
import FAQ from '@/components/Faq';
import {OrbitalLogoMark} from "@/components/icons/orbital-logo";
import {TaxiLogo} from "@/components/icons/taxi-icon-yellow";
import {microservicesCodeSnippets, publishYourApiCodeSnippets} from "@/components/home/code-snippets";
import {EmbedAndQuery} from "@/components/home/EmbedAndQuery";

const faqs = [
  {
    question: "Is Taxi open source?",
    answer: ["Yes. Taxi, TaxiQL, and the TaxiQL execution engine are all open source under Apache 2.0"]
  },
  {
    question: 'How does this compare to GraphQL?',
    answer: [
      `Orbital gives you many of the benefits of GraphQL (API federation, custom response schemas), without having to move your tech stack over to GraphQl - instead working with your existing tech stack(s).`
    ],
    learnMore: '/docs#taxi-vs-graph-ql'
  },
  {
    question: "What do you mean by 'Adapts Automatically'?",
    answer: ["Orbital generates integration on-the-fly, so as your APIs change, integration automatically adapts.  We also have CI/CD tooling that can detect changes which Orbital can't recover from, and warn you ahead of deployment."]
  },
  {
    question: "What's the relationship between Taxi and Orbital?",
    answer: ["Orbital is a source-visible commercial platform built on top of Taxi. They're also the company that funds the development of Taxi and TaxiQL."]
  },
  {
    question: 'How many FAQs are appropriate?',
    answer: ["This feels (at least) one too many."]
  },
]

function HeroSection() {
  return (
    <header className='relative'>
      <div className='sm:px-6 md:px-8 dark:bg-brand-background'>
        <div className='font-brand dark:text-white mx-auto max-w-8xl flex items-center gap-8 flex-col my-16 relative'>
          <TaxiLogo className={'h-[100px]'} />
          <h2 className='font-light lg:text-6xl text-4xl leading-tight text-center'>
            A language for APIs, data<br />and connecting it all together.
          </h2>

          <p className='lg:text-2xl font-light max-w-4xl text-lg text-center'>
            Describe how your data and services relate across your entire ecosystem - <br />
            from APIs and databases to message queues and beyond.
          </p>
          <p className='lg:text-2xl font-light text-lg text-center'>
            Use TaxiQL to fetch data - without resolvers or glue code - and adapts as things change.
          </p>
          <div className='sm:mt-10 mt-8 mb-3 flex justify-center gap-6 text-base md:text-lg flex-wrap'>
            <GetStartedButton/>
            <LinkButton styles="hidden md:flex" link='https://playground.taxilang.org/' label='Try the playground'/>
          </div>
          <div className={'flex items-center gap-4'}>
            <div>Developed by</div>
            <a href='https://orbitalhq.com'>
              <OrbitalLogoMark className="hidden h-7 w-auto fill-slate-700 dark:fill-sky-100 lg:block"/>
            </a>

          </div>
        </div>
      </div>
    </header>
  );
}

export default function Home(
  {
    publishYourApiHighlightedSnippets,
    microserviceHighlightedSnippets
  }
) {
  return (
    <>
      <HeadMetaTags title="Taxi - Describe how your APIs and Data relate"/>
      <div className='overflow-hidden dark:bg-brand-background'>
        <HeroSection/>
      </div>
      <div className='overflow-hidden'>
        <EmbedAndQuery microservicesCodeSnippets={microserviceHighlightedSnippets} publishYourApiHighlightedSnippets={publishYourApiHighlightedSnippets} />
        {/*<FeaturesSection/>*/}
      </div>
      <div className="relative z-10">
        <FAQ faqs={faqs}/>
      </div>
      <Footer/>
    </>
  );
}

export function getStaticProps() {
  let {highlightCodeSnippets} = require('@/components/Guides/Snippets');

  return {
    props: {
      microserviceHighlightedSnippets: highlightCodeSnippets(microservicesCodeSnippets),
      publishYourApiHighlightedSnippets: highlightCodeSnippets(publishYourApiCodeSnippets),
    }
  };
}

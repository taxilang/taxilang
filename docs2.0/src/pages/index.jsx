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
import {IntegrationComparison} from "@/components/home/IntegrationComparison";

const faqs = [
  {
    question: "Is Taxi open source?",
    answer: ["Yes. Taxi, TaxiQL, and the TaxiQL execution engine are all open source under Apache 2.0"]
  },
  {
    question: 'How does this compare to GraphQL?',
    answer: [
      "Taxi provides many of the benefits of GraphQL - data federation and custom response schemas - without requiring resolvers or a single global schema. It integrates with your existing tech stack and supports more than just HTTP. TaxiQL works across all data sources and integration patterns, including Kafka streams, S3 buckets, API orchestration and batch workloads."
    ],
    learnMore: '/docs#taxi-vs-graph-ql'
  },
  {
    question: "How does TaxiQL adapt automatically?",
    answer: ["TaxiQL generates integration on-the-fly, powered by semantic metadata in your APIs. So as your APIs change, the integration automatically adapts."]
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
            Connect all your APIs & services <br />without integration code.
          </h2>

          <p className='lg:text-3xl lg:leading-10 font-light max-w-4xl text-lg text-center'>
            Tag your APIs.<br />
            Query for data using TaxiQL.<br />
            Taxi handles the orchestration.
          </p>
          <div className='sm:mt-10 mt-8 mb-3 flex justify-center gap-6 text-base md:text-lg flex-wrap'>
            <GetStartedButton link='https://playground.taxilang.org/' label='Try the playground'/>
            <LinkButton styles="hidden md:flex" link='/docs' label='Read the docs'/>
          </div>
          {/*<div className={'flex items-center gap-4'}>*/}
          {/*  <div>Developed by</div>*/}
          {/*  <a href='https://orbitalhq.com'>*/}
          {/*    <OrbitalLogoMark className="hidden h-7 w-auto fill-slate-700 dark:fill-sky-100 lg:block"/>*/}
          {/*  </a>*/}

          {/*</div>*/}
        </div>
      </div>
    </header>
  );
}

export default function Home(
  {
    publishYourApiHighlightedSnippets,
    microserviceHighlightedSnippets,
    highlightedJsCode
  }
) {
  return (
    <>
      <HeadMetaTags title="Taxi - Describe how your APIs and Data relate"/>
      <div className='overflow-hidden dark:bg-brand-background'>
        <HeroSection/>
      </div>
      <div className='overflow-hidden dark:bg-brand-background'>
        <IntegrationComparison highlightedJsCode={highlightedJsCode} />
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

  const javascriptCodeSnippet = `// Without Taxi: The integration code you actually write
async function getOrdersWithDetails(customerId) {
  const customer = await customerApi.getCustomer(customerId);
  const orders = await orderApi.getOrdersByCustomer(customerId);

  const enrichedOrders = await Promise.all(
    orders.map(async (order) => {
      const [shipping, payment] = await Promise.all([
        shippingApi.getTracking(order.trackingId).catch(() => null),
        paymentApi.getPayment(order.paymentId).catch(() => null),
      ]);

      return {
        orderId: order.id,
        total: order.total,
        customerName: customer.name,
        shippingStatus: shipping?.status ?? 'unknown',
        paymentMethod: payment?.method,
      };
    })
  );

  return enrichedOrders;
}`;

  return {
    props: {
      microserviceHighlightedSnippets: highlightCodeSnippets(microservicesCodeSnippets),
      publishYourApiHighlightedSnippets: highlightCodeSnippets(publishYourApiCodeSnippets),
      highlightedJsCode: highlightCodeSnippets({'js-integration': {name: 'integration.js', lang: 'javascript', code: javascriptCodeSnippet}})['js-integration'],
    }
  };
}

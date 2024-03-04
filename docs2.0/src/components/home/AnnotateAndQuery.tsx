import {Snippet} from "@/components/Steps";
import {microservicesCodeSnippets} from "@/pages/microservices-orchestration";
import {CodeSnippetMap} from "@/components/Guides/CodeSnippet";
import {useState} from "react";
import {BigText, Widont} from "@/components/home/common";
import * as React from "react";
import Image from "next/future/image";
import OrbitalLogo from "@/img/wormhole-aqua-transparent.png";

export default function AnnotateAndQuerySection({highlightedSnippets}) {
  const codeSnippetButtons = [
    {label: 'Definitions (Taxi)', snippet: 'taxi-simple'},
    {label: 'OpenAPI', snippet: 'open-api-example'},
    {label: 'Protobuf (gRPC)', snippet: 'protobuf-example'},
    {label: 'Database', snippet: 'database-example'},
    {label: 'CSV', snippet: 'csv-example'},
  ]

  const factBoxes = [
    {
      title: 'Self Repairing',
      text: 'Orbital is driven by your API specs, so as your APIs change integrations automatically adapt.'
    },
    {title: 'Decentralized', text: "There's no central mapping or integration code to maintain, so teams can own their own API definitions, and push updates as they release."},
    {
      title: 'Designed for the Different',
      text: "Orbital links on Taxi metadata, not field names - so it doesn't matter if two systems assign different field names to the same data element."
    },
  ]

  const [activeCodeSnippet, setActiveCodeSnippet] = useState('taxi-simple')

  return (
    <section id="where-to-use" className={`relative bg-slate-900 pt-20 px-8`}>
      <Image src={OrbitalLogo} className={'absolute blur-3xl opacity-25 pointer-events-none'}
             alt="Orbital aqua wormhole background image"/>
      <div className='max-w-7xl mx-auto'>
        <BigText className='text-center font-brand pb-4'>

          <span className='text-citrus'>Wave goodbye</span><Widont> to integration code</Widont>
        </BigText>
        <div className='mb-20 text-lg text-slate-300 lg:text-center max-w-4xl mx-auto'>
          <p className='text-3xl my-4 leading-relaxed'>Add tags to your API specs. Query for data. <br/> Orbital handles
            the rest.</p>
          <p className='mt-8'>There's no resolvers or glue code to maintain, API clients to generate, or YAML mapping files. </p>
          <p>Drive everything from your API specs, and deploy from Git</p>
        </div>

        <div
          className='mt-6 grid w-full grid-cols-1 lg:grid-cols-2 text-slate-200 text-lg text-left gap-8'>

          <section>
            <p className='mb-8'>Define a set of terms, and embed them in your API specs</p>

          </section>
          <section>
            <p>Use those same terms to query for data. </p>
            <p>Orbital handles connecting to the right systems</p>
          </section>
          <section>
            <div className='mb-6'>
              {codeSnippetButtons.map(btn => {
                return (<button onClick={() => setActiveCodeSnippet(btn.snippet)}
                                type="button"
                                className="mr-4 rounded-full border-indigo-400 hover:border-indigo-500 border-2 px-3.5 py-2 text-sm font-semibold text-slate-200 shadow-sm hover:bg-indigo-300/10"
                >
                  {btn.label}
                </button>)
              })}
            </div>
            <Snippet highlightedCode={highlightedSnippets[activeCodeSnippet]}
                     code={microservicesCodeSnippets[activeCodeSnippet]}/>
          </section>
          <section>
            <Snippet highlightedCode={highlightedSnippets['taxi-query']}
                     code={microservicesCodeSnippets['taxi-query']}/>
          </section>
        </div>
        <div className={'py-16 md:grid grid-cols-3 gap-10 w-full mx-auto'}>
          {factBoxes.map((fact) => {
            return (
              <div className='ring-1 ring-insert rounded-lg ring-violet-500 px-6 py-4 mb-8 md:mb-0'>
                <h4 className='text-xl font-bold text-white pb-4'>{fact.title}</h4>
                <div className='text-slate-300'>{fact.text}</div>
              </div>
            )
          })}
        </div>
      </div>
    </section>

  )
}

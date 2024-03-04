import {useEffect, useState} from "react";
import {highlightCode} from "../../../remark/utils";
import Prism from '@/components/prismClientSide'
import {Snippet} from "@/components/Steps";
import {Editor} from "@/components/Editor";
import {useRouter} from "next/router";
import {TabBar} from "@/components/Guides/TabBar";

export function GuideSteps({children}) {
  return (
    <div>
      {children}
    </div>)
}

export function ClientSideCode({name, lang, code, children}) {
  useEffect(() => {
    Prism.highlightAll()
  }, [code]);

  const innerHtml = (code) ? code : children;
  return (
    <Editor key={name} filename={name}>
      <div className={'py-2 px-4'}>
        <code className={`language-${lang} block whitespace-pre-wrap`}
          dangerouslySetInnerHTML={{__html: innerHtml}}>
        </code>
      </div>
    </Editor>
  )
}

export function GuideStep({title, stepNumber, wide, children}) {

  const [text, snippet] = children || [];
  const grid = wide ? 'mb-4' : 'grid';
  const snippetPadding = wide ? 'mt-6' : ''

  return (
    <div className={'mb-8'}>
      <div className={`${grid} grid-cols-2 gap-4`}>
        <div className={'flex'}>
          {(stepNumber && <div className='mr-4 text-xs font-bold text-white px-3 py-2 bg-slate-600 rounded-md h-fit'>{stepNumber}</div>)}
          <div>
            <div className='font-medium text-sm text-slate-50 '>{title}</div>
            <div>{text}</div>
          </div>
        </div>

        <div className={`${snippetPadding} col-span-2 md:col-span-1`}>{snippet}</div>
      </div>
    </div>
  )
}


export function TabbedSteps({stepData}) {
  const [selectedTabIndex, setSelectedTabIndex] = useState(0)
  const tabs = stepData.map(entry => {
    return {
      name: entry.framework,
      href: entry.slug || `#${entry.framework}`
    }
  })
  const {asPath} = useRouter()
  useEffect(() => {
    const hash = asPath.split('#')[1]
    let tabIndex = tabs.findIndex((tab) => tab.href.replace('#','') === hash)
    if (tabIndex === -1) {
      tabIndex = 0
    }
    setSelectedTabIndex(tabIndex);
  }, [asPath]);

  const activeTabContent = stepData[selectedTabIndex].steps;
  return (
    <>
      <TabBar tabs={tabs} selectedTabIndex={selectedTabIndex}></TabBar>
      {activeTabContent()}
    </>
  )
}

import {useCallback, useEffect, useState} from 'react'
import Link from 'next/link'
import {useRouter} from 'next/router'
import clsx from 'clsx'

import {Hero} from '@/components/Hero'
import {Navigation} from '@/components/docs/Navigation'
import {Prose} from '@/components/docs/Prose'
import {SidebarLinks} from "@/components/docs/SidebarLinks";
import {MobileNavigation} from '@/components/docs/MobileNavigation';


function GitHubIcon(props) {
  return (
    <svg aria-hidden="true" viewBox="0 0 16 16" {...props}>
      <path
        d="M8 0C3.58 0 0 3.58 0 8C0 11.54 2.29 14.53 5.47 15.59C5.87 15.66 6.02 15.42 6.02 15.21C6.02 15.02 6.01 14.39 6.01 13.72C4 14.09 3.48 13.23 3.32 12.78C3.23 12.55 2.84 11.84 2.5 11.65C2.22 11.5 1.82 11.13 2.49 11.12C3.12 11.11 3.57 11.7 3.72 11.94C4.44 13.15 5.59 12.81 6.05 12.6C6.12 12.08 6.33 11.73 6.56 11.53C4.78 11.33 2.92 10.64 2.92 7.58C2.92 6.71 3.23 5.99 3.74 5.43C3.66 5.23 3.38 4.41 3.82 3.31C3.82 3.31 4.49 3.1 6.02 4.13C6.66 3.95 7.34 3.86 8.02 3.86C8.7 3.86 9.38 3.95 10.02 4.13C11.55 3.09 12.22 3.31 12.22 3.31C12.66 4.41 12.38 5.23 12.3 5.43C12.81 5.99 13.12 6.7 13.12 7.58C13.12 10.65 11.25 11.33 9.47 11.53C9.76 11.78 10.01 12.26 10.01 13.01C10.01 14.08 10 14.94 10 15.21C10 15.42 10.15 15.67 10.55 15.59C13.71 14.53 16 11.53 16 8C16 3.58 12.42 0 8 0Z"/>
    </svg>
  )
}
function useTableOfContents(tableOfContents) {
  if (tableOfContents === undefined || tableOfContents === null) {
    console.log('Did not receive a tableOfContents!')
    return;
  }
  let [currentSection, setCurrentSection] = useState(tableOfContents[0]?.slug)

  let getHeadings = useCallback((tableOfContents) => {
    return tableOfContents
      .flatMap((node) => [node.slug, ...node.children.map((child) => child.slug)])
      .map((slug) => {
        let el = document.getElementById(slug)
        if (!el) return

        let style = window.getComputedStyle(el)
        let scrollMt = parseFloat(style.scrollMarginTop)

        let top = window.scrollY + el.getBoundingClientRect().top - scrollMt - 40
        return {slug, top}
      })
  }, [])

  useEffect(() => {
    if (tableOfContents === undefined || tableOfContents.length === 0) return

    function onScroll(headings) {
      if (headings === undefined) {
        let headings1 = getHeadings(tableOfContents);
        onScroll(headings1)
        return;
      }
      let top = window.scrollY
      let current = headings[0]?.slug
      if (!current) {
        return;
      }

      for (let heading of headings) {
        if (top >= heading.top) {
          current = heading.slug
        } else {
          break
        }
      }
      setCurrentSection(current)
    }

    const headings = getHeadings(tableOfContents)
    const handleScroll = () => onScroll(headings)

    window.addEventListener('scroll', handleScroll, {passive: true})
    onScroll()
    return () => {
      window.removeEventListener('scroll', handleScroll, {passive: true})
    }
  }, [getHeadings, tableOfContents])

  return currentSection
}

const navigation = SidebarLinks

const GetInTouchMethods = [
  { label: 'Submit an issue', link: 'https://github.com/taxilang/taxilang/issues'},
  { label: 'Start a discussion', link: 'https://github.com/taxilang/taxilang/discussions'},
  { label: 'Chat on Slack', link: 'https://join.slack.com/t/orbitalapi/shared_invite/zt-697laanr-DHGXXak5slqsY9DqwrkzHg'},
  { label: 'Tweet us', link: 'https://twitter.com/orbitalapi'},
]

export function DocsLayout({children, tableOfContents, meta, slug}) {
  let router = useRouter()
  let isHomePage = router.pathname === '/'
  let allLinks = navigation.flatMap((section) => section.links)
  let linkIndex = allLinks.findIndex((link) => link.href === router.pathname)
  let previousPage = allLinks[linkIndex - 1]
  let nextPage = allLinks[linkIndex + 1]
  let section = navigation.find((section) =>
    section.links.find((link) => link.href === router.pathname)
  )
  const title = meta.title;

  let currentSection = useTableOfContents(tableOfContents)

  function isActive(section) {
    if (section.slug === currentSection) {
      return true
    }
    if (!section.children) {
      return false
    }
    return section.children.findIndex(isActive) > -1
  }

  return (
    <>
      {isHomePage && <Hero/>}

      <div className="relative mx-auto flex max-w-[96rem] justify-center sm:px-2 lg:px-8 xl:px-12">
        <div className="hidden lg:relative lg:block lg:flex-none">
          <div className="absolute inset-y-0 right-0 w-[50vw] bg-slate-50 dark:hidden"/>
          <div
            className="absolute top-16 bottom-0 right-0 hidden h-12 w-px bg-gradient-to-t from-slate-800 dark:block"/>
          <div className="absolute top-28 bottom-0 right-0 hidden w-px bg-slate-800 dark:block"/>
          <div
            className="sticky top-[5.25rem] -ml-0.5 h-[calc(100vh-5.25rem)] overflow-y-auto py-16 pl-0.5">
            <Navigation
              navigation={navigation}
              className="w-64 pr-8 xl:w-72 xl:pr-16"
            />
          </div>
        </div>
        <div className="ml-3 md:ml-6 sticky top-[6rem] md:top-[7rem] self-start flex lg:hidden">
          <MobileNavigation navigation={navigation}/>
        </div>
        <div className="min-w-0 max-w-2xl flex-auto lg:max-w-none p-4 md:p-8 lg:p-16">
          <article>
            {(title || section) && (
              <header className="mb-9 space-y-1">
                {section && (
                  <p className="font-display text-sm font-medium text-emerald-500">
                    {section.title}
                  </p>
                )}
                {title && (
                  <h1 className="font-display text-3xl tracking-tight text-slate-900 dark:text-white">
                    {title}
                  </h1>
                )}
              </header>
            )}

            <Prose>{children}</Prose>

          </article>
          <dl className="mt-12 flex border-t border-slate-200 pt-6 dark:border-slate-800">
            {previousPage && (
              <div>
                <dt className="font-display text-sm font-medium text-slate-900 dark:text-white">
                  Previous
                </dt>
                <dd className="mt-1">
                  <Link
                    href={previousPage.href}
                  >
                    <div className="cursor-pointer text-base font-semibold text-slate-500 hover:text-slate-600 dark:text-slate-400 dark:hover:text-slate-300">
                      <span aria-hidden="true">&larr;</span> {previousPage.title}
                    </div>
                  </Link>
                </dd>
              </div>
            )}
            {nextPage && (
              <div className="ml-auto text-right">
                <dt className="font-display text-sm font-medium text-slate-900 dark:text-white">
                  Next
                </dt>
                <dd className="mt-1">
                  <Link
                    href={nextPage.href}
                  >
                    <div className="cursor-pointer text-base font-semibold text-slate-500 hover:text-slate-600 dark:text-slate-400 dark:hover:text-slate-300">
                      {nextPage.title} <span aria-hidden="true">&rarr;</span>
                    </div>
                  </Link>
                </dd>
              </div>
            )}
          </dl>
        </div>
        <div
          className="hidden md:block md:-mr-6 md:py-8 lg:py-16 md:pr-6 md:sticky md:top-[5.25rem] md:h-[calc(100vh-5.25rem)] md:flex-none md:overflow-y-auto md:overflow-x-hidden">
          <nav aria-labelledby="on-this-page-title" className="w-56">
            {tableOfContents && tableOfContents.length > 0 && (
              <>
                <h2
                  id="on-this-page-title"
                  className="font-display text-sm font-medium text-slate-900 dark:text-white"
                >
                  On this page
                </h2>
                <ol role="list" className="mt-4 space-y-3 text-sm mb-20">
                  {tableOfContents.map((section, index) => (
                    <li key={`${section.id}${index}`}>
                      <h3>
                        <Link href={`#${section.slug}`}>
                          <a
                            className={clsx(
                              isActive(section)
                                ? 'text-yellow-500'
                                : 'font-normal text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
                            )}
                          >{section.title}</a>

                        </Link>
                      </h3>
                      {section.children.length > 0 && (
                        <ol
                          role="list"
                          className="mt-2 space-y-3 pl-5 text-slate-500 dark:text-slate-400"
                        >
                          {section.children.map((subSection, index) => (
                            <li key={subSection.slug+index}>
                              <Link
                                href={`#${subSection.slug}`}
                              >
                                <a className={
                                  isActive(subSection)
                                    ? 'text-yellow-400'
                                    : 'hover:text-slate-600 dark:hover:text-slate-300'
                                }>{subSection.title}</a>
                              </Link>
                            </li>
                          ))}
                        </ol>
                      )}
                    </li>
                  ))}
                </ol>
              </>
            )}
            <h2
              id="get-in-touch-title"
              className="font-display text-sm font-medium text-slate-900 dark:text-white"
            >
              Get in touch
            </h2>
            <ol role="list" className="mt-4 space-y-3 text-sm">
              {GetInTouchMethods.map((method) => {
                return (<li key={method.label}>
                  <Link href={method.link}>
                    <a className='hover:text-slate-600 dark:hover:text-slate-300'>{method.label}</a>
                  </Link>
                </li>)})
              }
            </ol>
          </nav>
        </div>
      </div>

    </>
  )
}

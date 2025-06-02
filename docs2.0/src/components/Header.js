import Link from 'next/link';
import {SearchButton} from '@/components/Search';
import Router from 'next/router';
import {Logo} from '@/components/Logo';
import {Dialog, Transition} from '@headlessui/react';
import {Fragment, useEffect, useState} from 'react';
import clsx from 'clsx';


export function NavPopover({display = 'md:hidden', className, ...props}) {
  let [isOpen, setIsOpen] = useState(false)

  useEffect(() => {
    if (!isOpen) return

    function handleRouteChange() {
      setIsOpen(false)
    }

    Router.events.on('routeChangeComplete', handleRouteChange)
    return () => {
      Router.events.off('routeChangeComplete', handleRouteChange)
    }
  }, [isOpen])

  return (
    <div className={clsx(className, display)} {...props}>
      <button
        type="button"
        className="text-zinc-500 w-8 h-8 flex items-center justify-center hover:text-zinc-600 dark:text-zinc-400 dark:hover:text-zinc-300"
        onClick={() => setIsOpen(true)}
      >
        <span className="sr-only">Navigation</span>
        <svg width="24" height="24" fill="none" aria-hidden="true">
          <path
            d="M12 6v.01M12 12v.01M12 18v.01M12 7a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm0 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm0 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      <Dialog
        as="div"
        className={clsx('fixed z-50 inset-0', display)}
        open={isOpen}
        onClose={setIsOpen}
      >
        <Dialog.Overlay className="fixed inset-0 bg-black/20 backdrop-blur-sm dark:bg-zinc-900/80"/>
        <div
          className="fixed top-4 right-4 w-full max-w-xs bg-white rounded-lg shadow-lg p-6 text-base font-semibold text-zinc-900 dark:bg-zinc-800 dark:text-zinc-400 dark:highlight-white/5">
          <button
            type="button"
            className="absolute z-10 top-5 right-5 w-8 h-8 flex items-center justify-center text-zinc-500 hover:text-zinc-600 dark:text-zinc-400 dark:hover:text-zinc-300"
            onClick={() => setIsOpen(false)}
          >
            <span className="sr-only">Close navigation</span>
            <svg viewBox="0 0 10 10" className="w-2.5 h-2.5 overflow-visible" aria-hidden="true">
              <path
                d="M0 0L10 10M10 0L0 10"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
          </button>
          <ul className="space-y-6">
            <NavItems/>
            <li>
              <a
                href="https://github.com/taxilang/taxilang"
                className="hover:text-sky-500 dark:hover:text-sky-400"
              >
                GitHub
              </a>
            </li>
          </ul>
        </div>
      </Dialog>
    </div>
  )
}

export function NavItems() {
  return (
    <>
      {/*<li className="whitespace-nowrap">
        <ContactUsButton/>
      </li>*/}
      <li className="whitespace-nowrap">
        <Link href="/docs">
          <a className="transition-colors hover:text-taxi-yellow-600 dark:hover:text-yellow-500">Docs</a>
        </Link>
      </li>
      <li className="whitespace-nowrap">
        <Link href="/changelog">
          <a className="transition-colors hover:text-yellow-600 dark:hover:text-yellow-500">Changelog</a>
        </Link>
      </li>
      <li className="whitespace-nowrap">
        <Link href="https://playground.taxilang.org">
          <a className="transition-colors hover:text-yellow-600 dark:hover:text-yellow-500">Playground</a>
        </Link>
      </li>
      <li className="whitespace-nowrap">
        <Link href="/docs/language/stdlib">
          <a className="transition-colors hover:text-yellow-600 dark:hover:text-yellow-500">StdLib</a>
        </Link>
      </li>
      <li className="whitespace-nowrap">
        <Link href="https://orbitalhq.com/blog">
          <a className="transition-colors hover:text-yellow-600 dark:hover:text-yellow-500">Blog</a>
        </Link>
      </li>
    </>
  )
}

export function Header({
                        showLogo = true,
                         hasNav = false,
                         title,
                         section,
                         allowThemeToggle = false,
                         className = '',
                         showSearch
                       }) {
  let [isOpaque, setIsOpaque] = useState(false)

  useEffect(() => {
    let offset = 50

    function onScroll() {
      if (!isOpaque && window.scrollY > offset) {
        setIsOpaque(true)
      } else if (isOpaque && window.scrollY <= offset) {
        setIsOpaque(false)
      }
    }

    onScroll()
    window.addEventListener('scroll', onScroll, {passive: true})
    return () => {
      window.removeEventListener('scroll', onScroll, {passive: true})
    }
  }, [isOpaque])

  return (
    <>
      <div
        className={clsx(
          'sticky top-0 z-40 w-full flex-none transition-colors duration-500 lg:z-50 ',
          isOpaque
            ? 'bg-white supports-backdrop-blur:bg-white/95 dark:bg-zinc-900/80 backdrop-blur border-b border-zinc-700/80'
            : 'bg-white/95 supports-backdrop-blur:bg-white/60 dark:bg-transparent',
          className
        )}
      >
        <div className="max-w-8xl mx-auto">
          <div
            className={clsx(
              'py-4 sm:px-0 pt-5',
              hasNav ? 'mx-4 lg:mx-0' : ''
            )}
          >
            <div className="relative flex justify-between items-center">
              <div className='left mr-auto'>
                {showLogo && (
                <Link href='/'>
                  <a
                    className='mr-3 flex items-center overflow-hidden'
                    onContextMenu={(e) => {
                      e.preventDefault();
                      Router.push('/brand');
                    }}
                  >
                    <span className='sr-only'>Taxi home page</span>
                    <Logo className='w-auto h-12'/>
                  </a>
                </Link>)}
              </div>
              <div className='right flex items-center'>
                <div className='relative hidden md:flex items-center ml-auto'>
                  <nav className='text-sm leading-6 font-semibold text-zinc-700 dark:text-zinc-200'>
                    <ul className='flex space-x-8 items-center'>
                      <NavItems/>
                      {showSearch &&
                        <SearchButton/>
                      }
                    </ul>
                  </nav>
                  <div className='flex items-center border-l border-zinc-200 ml-6 dark:border-gray-700/70'>
                    {allowThemeToggle && (<ThemeToggle panelClassName='mt-8'/>)}
                    <a
                      href='https://github.com/taxilang/taxilang'
                      className="ml-6 block text-zinc-400 hover:text-zinc-500 dark:hover:text-zinc-300"
                    >
                      <span className="sr-only">Taxi on GitHub</span>
                      <svg
                        viewBox="0 0 16 16"
                        className="w-5 h-5"
                        fill="currentColor"
                        aria-hidden="true"
                      >
                        <path
                          d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
                      </svg>
                    </a>
                  </div>
                </div>
                {showSearch &&
                  <SearchButton className="hidden sm:flex"/>
                }
                <NavPopover className="ml-2 -my-1" display="md:hidden"/>
              </div>
            </div>
          </div>
          {hasNav && (
            <div className="flex items-center p-4 border-b border-zinc-900/10 lg:hidden dark:border-zinc-50/[0.06]">
              <button
                type="button"
                onClick={() => onNavToggle(!navIsOpen)}
                className="text-zinc-500 hover:text-zinc-600 dark:text-zinc-400 dark:hover:text-zinc-300"
              >
                <span className="sr-only">Navigation</span>
                <svg width="24" height="24">
                  <path
                    d="M5 6h14M5 12h14M5 18h14"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                  />
                </svg>
              </button>
              {title && (
                <ol className="ml-4 flex text-sm leading-6 whitespace-nowrap min-w-0">
                  {section && (
                    <li className="flex items-center">
                      {section}
                      <svg
                        width="3"
                        height="6"
                        aria-hidden="true"
                        className="mx-3 overflow-visible text-zinc-400"
                      >
                        <path
                          d="M0 0L3 3L0 6"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.5"
                          strokeLinecap="round"
                        />
                      </svg>
                    </li>
                  )}
                  <li className="font-semibold text-zinc-900 truncate dark:text-zinc-200">
                    {title}
                  </li>
                </ol>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  )
}

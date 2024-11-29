import { useEffect, useState } from 'react'
import Link from 'next/link'
import Router, { useRouter } from 'next/router'
import { Dialog } from '@headlessui/react'

import { Navigation } from '@/components/docs/Navigation'
import {Logo} from '@/components/Logo';

function MenuIcon(props) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      strokeWidth="2"
      strokeLinecap="round"
      {...props}
    >
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  )
}

function CloseIcon(props) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      strokeWidth="2"
      strokeLinecap="round"
      {...props}
    >
      <path d="M5 5l14 14M19 5l-14 14" />
    </svg>
  )
}

export function MobileNavigation({ navigation }) {
  let router = useRouter()
  let [isOpen, setIsOpen] = useState(false)

  useEffect(() => {
    if (!isOpen) return

    function onRouteChange() {
      setIsOpen(false)
    }

    router.events.on('routeChangeComplete', onRouteChange)
    router.events.on('routeChangeError', onRouteChange)

    return () => {
      router.events.off('routeChangeComplete', onRouteChange)
      router.events.off('routeChangeError', onRouteChange)
    }
  }, [router, isOpen])

  return (
    <>
      <button
        type="button"
        onClick={() => setIsOpen(true)}
        className="relative"
        aria-label="Open navigation"
      >
        <MenuIcon className="h-6 w-6 stroke-slate-500" />
      </button>
      <Dialog
        open={isOpen}
        onClose={setIsOpen}
        className="fixed inset-0 z-50 flex items-start overflow-y-auto dark:bg-brand-background/50 pr-10 backdrop-blur lg:hidden"
        aria-label="Navigation"
      >
        <Dialog.Panel className="min-h-full w-full max-w-xs bg-white px-4 pt-5 pb-12 dark:bg-brand-background sm:px-6">
          <div className="flex items-center">
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label="Close navigation"
            >
              <CloseIcon className="h-6 w-6 stroke-slate-500" />
            </button>
            <Link href='/'>
              <a
                className='mr-3 flex items-center overflow-hidden'
                onContextMenu={(e) => {
                  e.preventDefault();
                  Router.push('/brand');
                }}
              >
                <span className='sr-only'>Taxi home page</span>
                <Logo className='w-auto ml-4 h-7'/>
              </a>
            </Link>
          </div>
          <Navigation navigation={navigation} className="mt-5 px-1" />
        </Dialog.Panel>
      </Dialog>
    </>
  )
}

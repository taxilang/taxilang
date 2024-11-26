import {cn} from '@/lib/utils';
import {Disclosure} from '@headlessui/react';
import {MinusSmallIcon, PlusSmallIcon} from '@heroicons/react/24/outline';
import {LinkButton} from "@/components/LinkButton";

interface FaqProps {
  faqs: { question: string, answer: string[], learnMore?: string }[];
}

const FAQ = ({faqs}: FaqProps) => {
  return (
    <div className="mx-8 py-18 sm:pt-16 lg:py-20">
      <div className="bg-[#0c0a09] ring-1 mx-auto rounded-2xl shadow-lg max-w-7xl lg:grid lg:grid-cols-12 lg:gap-12 p-6 sm:p-8 md:p-12">
        <div className="lg:col-span-5">
          <h2 className="text-4xl font-bold leading-10 tracking-tight text-citrus">Frequently asked questions</h2>
          <p className="mt-4 text-lg leading-7 text-gray-400">
            Got another gnarly question? We'd love to hear it. Come and chat on {' '}
            <a href="https://join.slack.com/t/orbitalapi/shared_invite/zt-697laanr-DHGXXak5slqsY9DqwrkzHg"
               target="_blank"
               className="font-semibold text-yellow-500 hover:underline underline-offset-4">
              Slack.
            </a>
          </p>
        </div>
        <div className="w-full lg:col-span-7">
          <dl className="mt-6 space-y-4 divide-y divide-white/10">
            {faqs.map((faq, index) => (
              <Disclosure key={index} as="div" className="pt-6">
                {({open}) => (
                  <>
                    <dt>
                      <Disclosure.Button className="flex w-full text-white/80 transition-colors hover:text-yellow-500 items-start justify-between text-left">
                        <span className={cn('text-base font-semibold leading-7 transition-colors', {'text-yellow-500': open})}>{faq.question}</span>
                        <span className="ml-6 flex h-7 items-center">
                              {open ? (
                                <MinusSmallIcon className="h-6 w-6 text-yellow-500" aria-hidden="true"/>
                              ) : (
                                <PlusSmallIcon className="h-6 w-6" aria-hidden="true"/>
                              )}
                            </span>
                      </Disclosure.Button>
                    </dt>
                    <Disclosure.Panel as="dd" className="mt-4 pr-12 text-base leading-7 text-gray-300">
                      { faq.answer.map((text, index) => <p key={index} className={'mb-6'}>{text}</p> )}
                      { faq.learnMore && (<LinkButton styles='w-fit' label='Learn more' link={faq.learnMore} />)}
                    </Disclosure.Panel>
                  </>
                )}
              </Disclosure>
            ))}
          </dl>
        </div>
      </div>
    </div>
  )
}

export default FAQ

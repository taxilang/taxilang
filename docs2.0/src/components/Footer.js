import Link from 'next/link';
import { Logo } from '@/components/Logo';

const footerNav = [
  {
    'Getting Started': [
      { title: 'Quick start', href: 'https://orbitalhq.com/docs' },
      // { title: 'Connecting OpenAPI Services', href: '/docs' },
      // { title: 'Connecting Message Queues', href: '/docs' },
      { title: 'Connecting a Kafka topic', href: 'https://orbitalhq.com/docs/describing-data-sources/connect-kafka-topic' },
      { title: 'Connecting Databases', href: 'https://orbitalhq.com/docs/connecting-data-sources/connecting-a-database' },
      { title: 'Querying for data', href: 'https://orbitalhq.com/docs/querying/writing-queries' },
      // { title: 'Building data pipelines', href: '/docs' }
    ],
    Guides: [
      { title: 'APIs, DBs and Queues - A flyby of Orbital ', href: 'https://orbitalhq.com/docs/guides/apis-databases-kafka' },
      { title: 'Building a backend for frontend', href: 'https://orbitalhq.com/docs/guides/composing-api-and-database' },
      { title: 'Custom kafka streams', href: 'https://orbitalhq.com/docs/guides/streaming-data' },
      { title: 'Generating from code', href: 'https://orbitalhq.com/docs/guides/generating-taxi-from-code' }
    ],
    'Community and Tools': [
      { title: 'Taxi Playground', href: 'https://playground.taxilang.org' },
      { title: 'Orbital', href: 'https://orbitalhq.com' },
      { title: 'Slack', href: 'https://join.slack.com/t/orbitalapi/shared_invite/zt-697laanr-DHGXXak5slqsY9DqwrkzHg' },
      { title: 'GitHub', href: 'https://github.com/taxilang/taxilang' },
      { title: 'Twitter', href: 'https://twitter.com/orbitalapi' },
      { title: 'LinkedIn', href: 'https://www.linkedin.com/company/orbitalhq/' }
    ],
  },
]

export function Footer() {
  return (
    <footer className="pb-16 text-sm leading-6 sm:mt-14 mt-16">
      <div className="max-w-7xl mx-auto divide-y divide-slate-200 px-4 sm:px-6 md:px-8 dark:divide-slate-700">
        {footerNav.map((sections) => (
          <div
            key={Object.keys(sections).join(',')}
            className="flex-none gap-20 space-y-10 sm:space-y-8 lg:flex lg:space-y-0"
          >
            {Object.entries(sections).map(([title, items]) => (
              <div key={title}>
                <h2 className="font-semibold text-slate-900 dark:text-slate-100">{title}</h2>
                <ul className="mt-3 space-y-2">
                  {items.map((item) => {
                    const isExternalLink = item.href.includes('http')
                    return (
                      <li key={item.href}>
                        <Link href={item.href}>
                          <a className="hover:text-slate-900 dark:hover:text-slate-300" target={isExternalLink ? '_blank' : null}>
                            {item.title}
                          </a>
                        </Link>
                      </li>
                    )}
                  )}
                </ul>
              </div>
            ))}
          </div>
        ))}
        <div className="mt-16 pt-10">
          <Logo className="w-auto h-10"/>
        </div>
      </div>
    </footer>
  )
}

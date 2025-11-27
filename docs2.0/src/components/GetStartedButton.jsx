import NextLink from 'next/link'


export default function GetStartedButton(props) {
  const { label, link } = props

  return (
    <NextLink href={link}>
      <a
        className="bg-taxi-yellow min-w-[170px]
                hover:bg-taxi-yellow-300 focus:outline-none
                focus:ring-2 focus:ring-slate-400 focus:ring-offset-2 focus:ring-offset-slate-50
                text-brand-background font-semibold h-12 px-6 rounded-lg sm:flex-1 flex
                items-center justify-center">
        {label}
      </a>
    </NextLink>
  )
}

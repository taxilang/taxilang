import NextLink from "next/link";
import {ReactElement} from "react";

type LinkButtonProps = {
  label: string;
  link: string;
  icon?: ReactElement
  styles?: string;
}
export const LinkButton = ({ label, link, icon, styles } : LinkButtonProps) => {
  const isExternalLink = link.includes('http')
  const linkElement = (
    <a
      className={`${styles || ''} bg-midnight-blue hover:bg-slate-700 color-white text-white hover:text-taxi-yellow font-semibold h-12 px-6
                rounded-lg border-2 border-taxi-yellow  flex sm:flex-1 items-center justify-center`}
      href={isExternalLink ? link : null}
      target={isExternalLink ? '_blank' : null}
    >
      <div className={`w-4 mr-5 ${!icon ? 'hidden' : ''}`}>
        {icon}
      </div>
      {label}
    </a>
  )
  if (isExternalLink) {
    return linkElement
  } else {
    return (
      <NextLink href={link}>
        {linkElement}
      </NextLink>
    )
  }

}

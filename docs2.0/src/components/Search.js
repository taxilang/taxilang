import {useState, useCallback, useRef, createContext, useContext, useEffect} from 'react'
import {createPortal} from 'react-dom'
import Link from 'next/link'
import {useRouter} from 'next/router'
import {DocSearchModal, useDocSearchKeyboardEvents} from 'typesense-docsearch-react'
import clsx from 'clsx'

const SearchContext = createContext()

const docSearchConfig = {
  typesenseCollectionName: 'orbital-docs',
  typesenseServerConfig: {
    nodes: [
      // Cloud
      /*{
        host: 'q2cy9udgx7zoiaw1p-1.a1.typesense.net',
        port: '443',
        protocol: 'https',
      },*/
      // Local
      /*{
        host: 'localhost',
        port: '8108',
        protocol: 'http'
      },*/
      // Marty's Herzer box
      {
        host: 'docsearch.orbitalhq.com',
        port: '443',
        protocol: 'https'
      },
    ],
    // Cloud & Local
    //apiKey: 'w5BazTsVHfzuNHBiX40faPdLV8kSKakW'
    // Marty's Herzer box
    apiKey: 'eWWGkJZnBPmuiwtbt3IHFtIPxMXpwatU'
  },
}

export function SearchProvider({children}) {
  const router = useRouter()
  const [isOpen, setIsOpen] = useState(false)
  const [initialQuery, setInitialQuery] = useState(null)
  const tags = ['docs', 'taxi', 'changelog', 'blog']; // The available tags
  const [filterTags, setFilterTags] = useState([...tags]);

  const onOpen = useCallback(() => {
    setIsOpen(true)
  }, [setIsOpen])

  const onClose = useCallback(() => {
    setIsOpen(false)
  }, [setIsOpen])

  const onInput = useCallback(
    (e) => {
      setIsOpen(true)
      setInitialQuery(e.key)
    },
    [setIsOpen, setInitialQuery]
  )

  useDocSearchKeyboardEvents({
    isOpen,
    onOpen,
    onClose,
    onInput
  })

  const modalWrapperRef = useRef(null); // Ref to the modal

  useEffect(() => {
    if (modalWrapperRef.current && isOpen) {
      createFilterCheckboxes()
    }
  }, [isOpen]);

  // TODO: we need to run the query again somehow when the tags change!
  /*useEffect(() => {
    console.log("run the search again!!!")
    setInitialQuery(query)
  }, [filterTags]);*/

  const createFilterCheckboxes = () => {
  // Find the SearchBar and Dropdown using their class names
    const searchBar = modalWrapperRef.current.querySelector('.DocSearch-SearchBar');
    const dropdown = modalWrapperRef.current.querySelector('.DocSearch-Dropdown');

    if (searchBar && dropdown) {
      const filterElements = document.createElement("div");
      filterElements.className = "filters"; // Optional: assign a class to the container

      tags.forEach(tag => {
        // Create a checkbox input element
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.id = `${tag}-checkbox`;
        checkbox.name = tag;
        checkbox.checked = filterTags.includes(tag);

        // Create a label for the checkbox
        const label = document.createElement("label");
        label.textContent = tag;
        label.setAttribute("for", `${tag}-checkbox`);

        // Add the event listener to the checkbox
        checkbox.addEventListener("change", (event) => {
          const checked = event.target.checked;
          const tagName = event.target.name;

          setFilterTags(prevTags =>
            checked ? [...prevTags, tagName] : prevTags.filter(t => t !== tagName)
          );
        });

        // Append the checkbox and label to the customElement div
        filterElements.appendChild(checkbox);
        filterElements.appendChild(label);
      });

      // Insert the custom element between the SearchBar and Dropdown
      searchBar.insertAdjacentElement('afterend', filterElements);
    }
  }

  return (
    <>
      <SearchContext.Provider
        value={{
          isOpen,
          onOpen,
          onClose,
          onInput,
        }}
      >
        {children}
      </SearchContext.Provider>
      {isOpen &&
        createPortal(
          <div ref={modalWrapperRef}>
            <DocSearchModal
              {...docSearchConfig}
              initialScrollY={window.scrollY}
              initialQuery={initialQuery}
              typesenseSearchParameters={{
                facet_by: 'tags',
                filter_by: `tags: [${filterTags.join(',')}]`
              }}
              placeholder="Search documentation"
              onClose={onClose}
              navigator={{
                navigate({itemUrl}) {
                  setIsOpen(false)
                  router.push(itemUrl)
                },
              }}
              hitComponent={Hit}
              transformItems={(items) => {
                return items?.map((item, index) => {
                  const a = document.createElement('a')
                  a.href = item.url

                  const hash = a.hash === '#content-wrapper' || a.hash === '#header' ? '' : a.hash

                  if (item.hierarchy?.lvl0) {
                    item.hierarchy.lvl0 = item.hierarchy.lvl0.replace(/&amp;/g, '&')
                  }

                  if (item._highlightResult?.hierarchy?.lvl0?.value) {
                    item._highlightResult.hierarchy.lvl0.value =
                      item._highlightResult.hierarchy.lvl0.value.replace(/&amp;/g, '&')
                  }

                  return {
                    ...item,
                    // We transform the absolute URL into a relative URL to
                    // leverage Next's preloading but only if it is a taxilang.org link.
                    url: item.url.includes('taxilang.org') ? `${a.pathname}${hash}` : item.url,
                    __is_result: () => true,
                    __is_parent: () => item.type === 'lvl1' && items.length > 1 && index === 0,
                    __is_child: () =>
                      item.type !== 'lvl1' &&
                      items.length > 1 &&
                      items[0].type === 'lvl1' &&
                      index !== 0,
                    __is_first: () => index === 1,
                    __is_last: () => index === items.length - 1 && index !== 0,
                  }
                })
              }}
            />
          </div>,
          document.body
        )}
    </>
  )
}

function Hit({hit, children}) {
  return (
    <Link href={hit.url} key={hit.url}>
      <a
        className={clsx({
          'DocSearch-Hit--Result': hit.__is_result?.(),
          'DocSearch-Hit--Parent': hit.__is_parent?.(),
          'DocSearch-Hit--FirstChild': hit.__is_first?.(),
          'DocSearch-Hit--LastChild': hit.__is_last?.(),
          'DocSearch-Hit--Child': hit.__is_child?.(),
        })}
        target={hit.url.includes('orbitalhq.com') ? '_blank' : '_self' }
        title={hit.url}
      >
        {children}
      </a>
    </Link>
  )
}

export function SearchButton({children, ...props}) {
  let searchButtonRef = useRef()
  let {onOpen, onInput} = useContext(SearchContext)

  useEffect(() => {
    // NOTE the logic in here doesn't fire at all - leaving it for now, as we need some way
    //      for this to update the query for when the filter tags are toggled on/off
    function onKeyDown(event) {
      if (searchButtonRef && searchButtonRef.current === document.activeElement && onInput) {
        if (/[a-zA-Z0-9]/.test(String.fromCharCode(event.keyCode))) {
          onInput(event)
        }
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [onInput, searchButtonRef])

  return (
    <button type="button"
            ref={searchButtonRef}
            onClick={onOpen}
            className={`flex shrink-0 items-center text-sm leading-6 text-zinc-400 rounded-md ring-1 ring-zinc-900/10 shadow-sm py-1.5 pl-2 pr-3 hover:ring-zinc-300 dark:bg-zinc-800 dark:highlight-white/5 dark:hover:bg-zinc-700 ${props.className}`}
      >
      <svg width="24" height="24" fill="none" aria-hidden="true" className="mr-3 flex-none">
        <path d="m19 19-3.5-3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round"
              strokeLinejoin="round"></path>
        <circle cx="11" cy="11" r="6" stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                strokeLinejoin="round"></circle>
      </svg>
      <span className="hidden lg:block">Search...</span><span className="hidden lg:block ml-auto pl-3 flex-none text-xs font-semibold text-zinc-500">[Ctrl K]</span>
    </button>
  )
}

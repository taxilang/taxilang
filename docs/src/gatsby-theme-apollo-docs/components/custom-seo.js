import PropTypes from "prop-types";
import React from "react";
import { SEO } from "gatsby-theme-apollo-core";
import getShareImage from "@jlengstorf/get-share-image";

export default function CustomSEO({ image, baseUrl, twitterHandle, ...props }) {
   const imagePath = getShareImage({
      title: props.title,
      tagline: props.description,
      cloudName: "notional",
      imagePublicID: "taxi/dark-blue-background",
      titleFont: "Source%20Sans%20Pro",
      taglineFont: "Source%20Sans%20Pro",
      textColor: "ffffff",
      titleFontSize: 70,
      taglineFontSize: 52,
      textLeftOffset: 50,
      titleBottomOffset: 450,
      taglineTopOffset: 250
   });
   return (
      <SEO
         {...props}
         twitterCard="summary_large_image"
         favicon="/taxi-lang-logo.png"
      >
         <meta property="og:image" content={imagePath} />
         {baseUrl && <meta name="twitter:image" content={imagePath} />}
         {twitterHandle && (
            <meta name="twitter:site" content={`@${twitterHandle}`} />
         )}
      </SEO>
   );
}

CustomSEO.propTypes = {
   baseUrl: PropTypes.string,
   image: PropTypes.string.isRequired,
   twitterHandle: PropTypes.string
};

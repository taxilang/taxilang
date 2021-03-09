import React from "react";

export default function Logo() {
   return (
      <>
         <img
            src="/taxi-lang-logo.png"
            style={{
               display: "inline",
               width: "auto",
               float: "left",
               height: 35
            }}
         />
         <span
            style={{
               fontSize: 24,
               fontFamily: "Content-font, Roboto, sans-serif",
               fontWeight: 500,
               lineHeight: 1.5,
               marginLeft: 4
            }}
         >
            taxi
         </span>
      </>
   );
}

import type { Context } from "@netlify/functions";

/**
 * This function is triggered automatically by netlify whenever a deploy succeeds
 * (because the file is named deploy-succeeded - see: https://docs.netlify.com/functions/trigger-on-events/#available-triggers)
 * It trigger a github build action, which updates the search indexes in our Typesense search server
 */
export default async (req: Request, context: Context) => {
  const githubToken = Netlify.env.get("GITHUB_TOKEN")
  const triggerResult = await fetch("https://api.github.com/repos/orbitalapi/docs-update-trigger/dispatches", {
    method: "POST",
    headers: {
      'Authorization' : `token ${githubToken}`
    },
    body: JSON.stringify({"event_type": "Trigger reindex from Netlify - Taxilang - Post-deploy"})
  })

  console.log(`Trigger returned status ${triggerResult.status} - ${triggerResult.statusText}`);
  return new Response(triggerResult.body)
}

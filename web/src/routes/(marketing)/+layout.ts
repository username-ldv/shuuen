// Marketing pages (landing, and future about/pricing/etc.) are static: no
// per-user data, so prerender the whole (marketing) group to plain HTML at
// build time. Dynamic app routes live in a sibling group and opt out.
export const prerender = true;

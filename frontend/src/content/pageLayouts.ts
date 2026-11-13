import type { ContentPageKey } from "../api/contentApi";

/*
 * The editable words of the Services, About and Privacy pages (UC-09).
 *
 * The pages keep their own design, photos and layout. An administrator can
 * only change the TEXT of the named slots below, within each slot's character
 * limit, and add or remove entries of the Services list. Nothing here can
 * change markup, colours, images or where things sit on the page.
 *
 * The "default" of every slot is the wording the page had before, so a page
 * that has never been edited looks exactly as it always did.
 *
 * What is stored for a page (as text, in the page body):
 *   { "fields": { "<slot key>": "text" }, "services": [ ... ] }
 */

export type SlotSpec = {
  key: string;
  label: string;
  group: string;
  max: number;
  multiline?: boolean;
  default: string;
};

export type ServiceItem = {
  id: string;
  title: string;
  text: string;
  comingSoon?: boolean;
};

export type PageContent = {
  fields: Record<string, string>;
  services?: ServiceItem[];
};

export type PageLayout = {
  label: string;
  path: string;
  slots: SlotSpec[];
  hasServices: boolean;
};

export const SERVICE_TITLE_MAX = 80;
export const SERVICE_TEXT_MAX = 300;
export const SERVICES_MAX = 12;

function slot(
  group: string,
  key: string,
  label: string,
  max: number,
  defaultText: string,
  multiline = false
): SlotSpec {
  return { key, label, group, max, multiline, default: defaultText };
}

export const DEFAULT_SERVICES: ServiceItem[] = [
  {
    id: "respite",
    title: "Respite",
    text: "One-to-one or small-group respite for children or adults, at home or in the community. Support can focus on life skills, financial literacy, art, new hobbies and goals chosen with the client and family.",
  },
  {
    id: "social-life-skills",
    title: "Social Skills and Life Skills Coaching",
    text: "Individual or small-group support for friendships, independent-living safety, relationships, employment or volunteer preparation and other goals based on the person’s needs.",
  },
  {
    id: "tutoring-technology",
    title: "Tutoring and Assistive Tech / Tech Coaching",
    text: "Tutoring and technology coaching informed by more than 18 years of experience with assistive technology, SEA equipment, Google, Microsoft, Apple and communication applications.",
  },
  {
    id: "counselling",
    title: "Counselling",
    text: "Individual counselling shaped around your needs and goals through an empathetic, strengths-based and neurodiversity-informed approach.",
  },
  {
    id: "adhd-coaching",
    title: "ADHD Coaching and Executive Function Coaching",
    text: "Practical tools for procrastination, task management, organization, emotional regulation, self-advocacy, manageable goals and supportive check-ins.",
  },
  {
    id: "animal-dbt",
    title: "Animal-Based DBT (Dialectical Behaviour Therapy)",
    text: "A developing service focused on emotional-regulation skills, confidence and meaningful connection. Contact Casey for current availability and details.",
    comingSoon: true,
  },
];

export const PAGE_LAYOUTS: Record<ContentPageKey, PageLayout> = {
  services: {
    label: "Services",
    path: "/services",
    hasServices: true,
    slots: [
      slot("Top of the page", "heroEyebrow", "Small heading above the title", 50, "Services and counselling supports"),
      slot("Top of the page", "heroTitle", "Main title", 90, "Support shaped around your needs, strengths and goals."),
      slot("Top of the page", "heroLead", "Introduction", 180, "Explore individualized counselling, coaching, tutoring, respite and technology supports for children, adults and families.", true),
      slot("Availability section", "availEyebrow", "Small heading", 45, "Availability and service area"),
      slot("Availability section", "availTitle", "Heading", 80, "Flexible support outside regular daytime hours"),
      slot("Availability section", "availP1", "First paragraph", 330, "Availability is currently limited because Casey works full time during the day and supports families in the community after hours. When time slots are open, weekday appointments may be available Monday to Friday from 5:00 p.m. to 9:00 p.m.", true),
      slot("Availability section", "availP2", "Second paragraph", 340, "Services may be available in Oakville, Burlington and Waterdown. Rates are reviewed according to support needs. If transportation is required for respite, mileage or an adjusted hourly rate may apply. A résumé and police check can be provided when appropriate.", true),
      slot("Services section", "listTitle", "Heading above the services", 40, "What I offer"),
      slot("Services section", "listIntro", "Sentence under the heading", 150, "Every service begins with a conversation about needs, strengths, preferences and practical goals.", true),
      slot("Technology section", "toolsEyebrow", "Small heading", 40, "Technology toolbox"),
      slot("Technology section", "toolsTitle", "Heading", 70, "Assistive technology and learning tools"),
      slot("Technology section", "toolsText", "Paragraph", 330, "Coaching may include Kurzweil, Read&Write, Mindomo, Google Workspace for Education, Microsoft 365, speech-to-text, text-to-speech, Google Keep, OneNote, Equatio and accessibility features built into commonly used applications and devices.", true),
      slot("Next steps section", "processEyebrow", "Small heading", 40, "What happens next"),
      slot("Next steps section", "processTitle", "Heading", 70, "A simple and respectful first step"),
      slot("Next steps section", "step1Title", "Step 1 word", 20, "Connect."),
      slot("Next steps section", "step1Text", "Step 1 sentence", 100, "Send a general question or begin the secure intake process."),
      slot("Next steps section", "step2Title", "Step 2 word", 20, "Discuss."),
      slot("Next steps section", "step2Text", "Step 2 sentence", 100, "Talk about needs, strengths, preferences and practical goals."),
      slot("Next steps section", "step3Title", "Step 3 word", 20, "Plan."),
      slot("Next steps section", "step3Text", "Step 3 sentence", 100, "Confirm whether a service is suitable and agree on the next step."),
      slot("Next steps section", "primaryButton", "First button wording", 30, "Begin secure intake"),
      slot("Next steps section", "secondaryButton", "Second button wording", 30, "Ask a question"),
    ],
  },

  about: {
    label: "About Casey",
    path: "/about",
    hasServices: false,
    slots: [
      slot("Top of the page", "heroEyebrow", "Small heading above the title", 40, "About Casey"),
      slot("Top of the page", "heroTitle", "Main title", 100, "Lived experience, professional knowledge and practical support."),
      slot("Top of the page", "heroLead", "Introduction", 210, "Casey is a BSW social worker with more than 16 years of experience supporting neurodiverse people and individuals with developmental disabilities.", true),
      slot("Top of the page", "heroButton", "Button wording", 30, "Start a conversation"),
      slot("Casey’s story", "storyEyebrow", "Small heading", 40, "Casey’s story"),
      slot("Casey’s story", "storyTitle", "Heading", 70, "Finding the tools that help us grow"),
      slot("Casey’s story", "storyP1", "First paragraph", 340, "At a young age, Casey felt that she did not fit into the typical academic box and that the standard path was not working. She worked hard at school, but her marks did not reflect her effort. At age 13, she was diagnosed with a learning disability and ADHD.", true),
      slot("Casey’s story", "storyP2", "Second paragraph", 400, "Casey firmly believes that we all learn differently and need to find the right tools to help us reach our goals. Assistive technology opened a world of possibilities for her. Her mother was a constant advocate and helped pave the way for their school district to use more technology to support students.", true),
      slot("Casey’s story", "storyP3", "Third paragraph", 200, "No matter the diagnosis or profile, Casey believes there is a tool or accessibility support that can help each person reach their potential.", true),
      slot("Professional experience", "expEyebrow", "Small heading", 40, "Professional experience"),
      slot("Professional experience", "expTitle", "Heading", 70, "Support across ages, needs and settings"),
      slot("Professional experience", "expP1", "First paragraph", 340, "Casey has worked in the developmental-disabilities field for more than 16 years in a variety of roles. She has supported clients and students with special needs, neurodiversities, dual diagnoses and developmental disabilities, from age three to adults over 60.", true),
      slot("Professional experience", "expP2", "Second paragraph", 270, "She especially values the “aha!” moment when a client or student discovers a tool or skill that works for them, feels proud of what they achieved and begins to believe more strongly in their abilities.", true),
      slot("Professional experience", "expQuote", "Highlighted quote", 170, "“I want to help you build your toolbox to work smarter, not harder. I offer tutoring, coaching, respite and counselling.”", true),
      slot("What guides the work", "valuesEyebrow", "Small heading", 40, "What guides the work"),
      slot("What guides the work", "valuesTitle", "Heading", 60, "Clear support with room to grow"),
      slot("What guides the work", "value1Title", "Box 1 title", 30, "Strengths first"),
      slot("What guides the work", "value1Text", "Box 1 sentence", 100, "Build from abilities, interests and existing successes."),
      slot("What guides the work", "value2Title", "Box 2 title", 30, "Practical tools"),
      slot("What guides the work", "value2Text", "Box 2 sentence", 130, "Choose strategies that can be used at home, school, work and in the community."),
      slot("What guides the work", "value3Title", "Box 3 title", 30, "Collaborative goals"),
      slot("What guides the work", "value3Text", "Box 3 sentence", 120, "Set meaningful next steps with the individual and family, not for them."),
      slot("What guides the work", "value4Title", "Box 4 title", 30, "Respect and dignity"),
      slot("What guides the work", "value4Text", "Box 4 sentence", 120, "Create an inclusive, affirming environment where people feel heard."),
      slot("Bottom of the page", "ctaTitle", "Heading", 100, "Would you like to see whether this approach fits your needs?"),
      slot("Bottom of the page", "ctaText", "Sentence", 110, "Ask a question or share what kind of support you are looking for."),
      slot("Bottom of the page", "ctaButton", "Button wording", 30, "Contact Casey"),
    ],
  },

  privacy: {
    label: "Privacy Policy",
    path: "/privacy",
    hasServices: false,
    slots: [
      slot("Privacy page", "title", "Title", 40, "Privacy"),
      slot("Privacy page", "p1", "First paragraph", 300, "This page is reserved for the client-approved privacy policy. Until that policy is finalized, do not submit sensitive or emergency information through the general contact form.", true),
      slot("Privacy page", "p2", "Second paragraph", 220, "Information submitted through secure intake is intended only to help determine appropriate support and next steps.", true),
      slot("Privacy page", "linkText", "Link wording", 60, "Contact Casey with a privacy question"),
    ],
  },
};

export const PAGE_KEYS: ContentPageKey[] = ["services", "about", "privacy"];

export function defaultPageContent(pageKey: ContentPageKey): PageContent {
  const layout = PAGE_LAYOUTS[pageKey];
  const fields: Record<string, string> = {};

  for (const item of layout.slots) {
    fields[item.key] = item.default;
  }

  return {
    fields,
    services: layout.hasServices
      ? DEFAULT_SERVICES.map((service) => ({ ...service }))
      : undefined,
  };
}

function readServices(value: unknown): ServiceItem[] | null {
  if (!Array.isArray(value) || value.length === 0) return null;

  const services: ServiceItem[] = [];

  for (const entry of value) {
    if (typeof entry !== "object" || entry === null) return null;

    const { id, title, text, comingSoon } = entry as Record<string, unknown>;

    if (
      typeof id !== "string" ||
      typeof title !== "string" ||
      typeof text !== "string" ||
      !title.trim() ||
      !text.trim() ||
      title.length > SERVICE_TITLE_MAX ||
      text.length > SERVICE_TEXT_MAX
    ) {
      return null;
    }

    services.push({ id, title, text, comingSoon: comingSoon === true });
  }

  return services.length <= SERVICES_MAX ? services : null;
}

/*
 * The stored text of a page, safely turned into page content. Anything that
 * is missing, not text, empty or longer than its limit falls back to the
 * page's own wording, so a damaged value can never break or reshape the page.
 */
export function resolvePageContent(
  pageKey: ContentPageKey,
  body: string | null | undefined
): PageContent {
  const content = defaultPageContent(pageKey);

  if (!body) return content;

  let parsed: unknown;

  try {
    parsed = JSON.parse(body);
  } catch {
    return content;
  }

  if (typeof parsed !== "object" || parsed === null) return content;

  const stored = parsed as { fields?: unknown; services?: unknown };

  if (typeof stored.fields === "object" && stored.fields !== null) {
    const values = stored.fields as Record<string, unknown>;

    for (const item of PAGE_LAYOUTS[pageKey].slots) {
      const value = values[item.key];

      if (
        typeof value === "string" &&
        value.trim() !== "" &&
        value.length <= item.max
      ) {
        content.fields[item.key] = value;
      }
    }
  }

  if (PAGE_LAYOUTS[pageKey].hasServices) {
    content.services = readServices(stored.services) ?? content.services;
  }

  return content;
}

export function serializePageContent(content: PageContent): string {
  return JSON.stringify(
    content.services
      ? { fields: content.fields, services: content.services }
      : { fields: content.fields }
  );
}

// Problems an administrator must fix before saving, keyed by field id.
export function validatePageContent(
  pageKey: ContentPageKey,
  content: PageContent
): Record<string, string> {
  const problems: Record<string, string> = {};

  for (const item of PAGE_LAYOUTS[pageKey].slots) {
    const value = (content.fields[item.key] ?? "").trim();

    if (!value) {
      problems[`slot-${item.key}`] = `${item.group}: ${item.label} cannot be empty.`;
    } else if (value.length > item.max) {
      problems[`slot-${item.key}`] = `${item.group}: ${item.label} must be ${item.max} characters or fewer.`;
    }
  }

  if (PAGE_LAYOUTS[pageKey].hasServices) {
    const services = content.services ?? [];

    if (services.length === 0) {
      problems["service-add"] = "Keep at least one service on the page.";
    }

    services.forEach((service, index) => {
      if (!service.title.trim()) {
        problems[`service-title-${index}`] = `Service ${index + 1}: the name cannot be empty.`;
      }

      if (!service.text.trim()) {
        problems[`service-text-${index}`] = `Service ${index + 1}: the description cannot be empty.`;
      }
    });
  }

  return problems;
}

// A short, unique, URL-safe id for a service an administrator adds.
export function newServiceId(existing: ServiceItem[]): string {
  const used = new Set(existing.map((service) => service.id));
  let number = existing.length + 1;

  while (used.has(`service-${number}`)) {
    number += 1;
  }

  return `service-${number}`;
}

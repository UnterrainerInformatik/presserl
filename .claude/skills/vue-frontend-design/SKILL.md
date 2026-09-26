---
name: vue-frontend-design
description: Use when mentioning vue or vue.js or when being asked to do a frontend-design (or UI or GUI).
license: MIT
metadata:
  author: "Gerald"
  repository: "https://github.com/UnterrainerInformatik/agent-skills"
  version: 1.0.0
  keywords: ai, agent, skill
---
# Vue Frontend Design Skill

Guide on how to design a frontend specifically in vue or vuejs.

## When to Use

- User says "Design a vue-frontend" / "Make a UI in vue" / "Build a GUI in vuejs"
## Rules
- Use Vue.js version 3 from https://vuejs.org/
- Use Vite as building-framework https://vite.dev/
- Use Pinia as the centeral store
- Use Axios as the REST-client
- Use Vitest as testing framework https://vitest.dev/
- Use Material Design for styling https://m3.material.io/
- Use iconsets from Google Material Design https://fonts.google.com/icons
	- Download the font (or use a embedding-library)
	- Don't use a CDN for the font!
- Use Vuetify version 3 (stable) for everything else https://vuetifyjs.com/
- Your application consists of Views (`./src/.vue` files), components (`./src/components/*.vue`), services (`./)
- Deployments are in separate root-folders called `deploy-deploymentName`
- The `./deployment` folder contains the staging-environments' deployment
- The `./public` folder contains the favicon
- The `./src/composables` folder contains the composables
- The `./src/locales` folder contains the localization
	- We are using BabelEdit https://www.codeandweb.com/babeledit for that
	- Standard is German (`de-AT.json`) and English `en-US.json`
	- Both files contain key-value pairs with the same key for both files and a different value
	- The files can contain nested object (but both files have the same structure; though they don't have the same values)
	- The `i18n.ts` file ties all other jsons together
- The `./src/plugin` folder contains the plugins
- The `./src/router` folder contains the routes
- The `./stores` folder contains the Pinia stores
- The `./src/utils` folder contains all general utils classes which should have plenty of unit-tests (coverage above 90% per file)
- The `./src/webservices` contains all REST-interface implementations and has a sub-folder named `interfaces` that contains the base-implementations for web-services
- The `./test` folder contains all unit- and integration-tests for the project
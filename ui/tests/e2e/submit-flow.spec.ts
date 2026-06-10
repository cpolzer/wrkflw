import { test, expect } from '@playwright/test'

// Requires: docker compose up -d, npm run dev, keycloak healthy
test('submit flow happy path', async ({ page }) => {
  // Login as alice (initiator)
  await page.goto('/')
  // OIDC redirect to Keycloak
  await page.waitForURL(/localhost:8180/)
  await page.fill('#username', 'alice')
  await page.fill('#password', 'password')
  await page.click('[type=submit]')
  await page.waitForURL(/localhost:5173/)

  // Navigate to submit form
  await page.goto('/submit/document-approval')
  await expect(page.getByRole('heading', { name: /submit/i })).toBeVisible()

  // Fill required fields
  await page.fill('[name=title]', 'Test Document')
  await page.fill('[name=description]', 'A test document for approval')
  await page.selectOption('[name=priority]', 'medium')

  // Submit
  await page.click('[type=submit]')

  // Should land on flow detail view
  await page.waitForURL(/\/flows\//)
  await expect(page.getByText('PENDING_REVIEW')).toBeVisible()
})

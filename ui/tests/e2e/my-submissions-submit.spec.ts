import { test, expect } from '@playwright/test'

// Requires: docker compose up -d, npm run dev, keycloak healthy
test.use({ browserName: 'chromium' })

async function loginAsAlice(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.waitForURL(/localhost:8180/)
  await page.fill('#username', 'alice')
  await page.fill('#password', 'password')
  await page.click('[type=submit]')
  // Wait for auth callback to finish processing (exchange code → token) and redirect away
  await page.waitForURL((url) => url.hostname === 'localhost' && !url.pathname.startsWith('/auth/'), { timeout: 15000 })
}

test('Submit new document button navigates to submit form', async ({ page }) => {
  await loginAsAlice(page)
  await page.goto('/submissions')
  await page.waitForURL(/\/submissions/)

  const submitBtn = page.getByText('Submit new document').first()
  await expect(submitBtn).toBeVisible({ timeout: 5000 })

  await submitBtn.click()
  await page.waitForURL(/\/submit\/document-approval/)
  await expect(page.getByRole('heading', { name: /document approval/i })).toBeVisible()
})

test('Full submission journey: My Submissions → form → flow detail', async ({ page }) => {
  await loginAsAlice(page)
  await page.goto('/submissions')
  await page.waitForURL(/\/submissions/)

  // Click "Submit new document" from the header CTA
  const submitBtn = page.getByText('Submit new document').first()
  await expect(submitBtn).toBeVisible({ timeout: 5000 })
  await submitBtn.click()
  await page.waitForURL(/\/submit\/document-approval/)

  // Fill required fields
  await page.fill('[name=title]', 'E2E Test Document')
  await page.fill('[name=description]', 'Created by e2e test suite')
  await page.selectOption('[name=priority]', 'medium')

  // Submit
  await page.click('[aria-label="Submit document for approval"]')

  // Should redirect to flow detail
  await page.waitForURL(/\/flows\//)
  await expect(page.getByText(/Submitted/)).toBeVisible()
})

test('Empty state CTA also navigates to submit form', async ({ page }) => {
  await loginAsAlice(page)

  await page.goto('/submissions')
  await page.waitForURL(/\/submissions/)

  const emptyStateCTA = page.locator('.onyx-empty').getByText('Submit new document')
  const hasEmptyState = await emptyStateCTA.isVisible().catch(() => false)

  if (!hasEmptyState) {
    // If alice already has submissions the empty state won't show — skip gracefully
    test.skip()
    return
  }

  await emptyStateCTA.click()
  await page.waitForURL(/\/submit\/document-approval/)
  await expect(page.getByRole('heading', { name: /document approval/i })).toBeVisible()
})

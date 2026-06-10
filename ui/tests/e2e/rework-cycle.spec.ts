import { test, expect } from '@playwright/test'

// Requires: full stack running (docker compose up -d + api-service + npm run dev)
test('full rework cycle: reject, notify, re-submit', async ({ page }) => {
  // Step 1: bob rejects alice's submitted flow
  await page.goto('/')
  await page.waitForURL(/localhost:8180/)
  await page.fill('#username', 'bob')
  await page.fill('#password', 'password')
  await page.click('[type=submit]')
  await page.waitForURL(/localhost:5173/)

  await page.goto('/worklist')
  const claimBtn = page.getByRole('button', { name: /claim/i }).first()
  await expect(claimBtn).toBeVisible({ timeout: 5000 })
  await claimBtn.click()

  const viewBtn = page.getByRole('link', { name: /view task/i }).first()
  await viewBtn.click()
  await page.waitForURL(/\/tasks\//)

  await page.click('[data-action=reject]')
  await page.fill('#reject-comment', 'Missing signature')
  await page.getByRole('button', { name: /confirm reject/i }).click()
  await expect(page.getByText(/rejected/i)).toBeVisible()

  // Step 2: alice sees rework badge and re-submits
  await page.context().clearCookies()
  await page.goto('/')
  await page.waitForURL(/localhost:8180/)
  await page.fill('#username', 'alice')
  await page.fill('#password', 'password')
  await page.click('[type=submit]')
  await page.waitForURL(/localhost:5173/)

  await page.goto('/submissions')

  // Rework badge should be visible in nav
  await expect(page.getByRole('navigation').getByText(/1/)).toBeVisible({ timeout: 5000 })

  // Click through to flow detail
  await page.getByRole('link', { name: /view/i }).first().click()
  await page.waitForURL(/\/flows\//)

  await expect(page.getByText(/returned for rework/i)).toBeVisible()
  await expect(page.getByText(/missing signature/i)).toBeVisible()

  // Re-submit
  await page.getByRole('button', { name: /re-submit/i }).click()
  await page.waitForURL(/\/flows\//)
  await expect(page.getByText(/pending review/i)).toBeVisible()
})

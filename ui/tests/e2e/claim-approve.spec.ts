import { test, expect } from '@playwright/test'

// Requires: full stack running (docker compose up -d + api-service + npm run dev)
test('claim and approve a task', async ({ page }) => {
  // Login as bob (reviewer)
  await page.goto('/')
  await page.waitForURL(/localhost:8180/)
  await page.fill('#username', 'bob')
  await page.fill('#password', 'password')
  await page.click('[type=submit]')
  await page.waitForURL(/localhost:5173/)

  // Should land on worklist (has pending tasks from prior submit)
  await page.goto('/worklist')
  const claimBtn = page.getByRole('button', { name: /claim/i }).first()
  await expect(claimBtn).toBeVisible({ timeout: 5000 })
  await claimBtn.click()

  // Navigate to task detail
  const viewBtn = page.getByRole('link', { name: /view task/i }).first()
  await viewBtn.click()
  await page.waitForURL(/\/tasks\//)

  // Approve
  await page.click('[data-action=approve]')
  await expect(page.getByText(/approved/i)).toBeVisible()
})

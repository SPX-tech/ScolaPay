import 'dotenv/config';
import { PrismaService } from '../src/prisma/prisma.service';
import { createHash, randomBytes } from 'node:crypto';

const prisma = new PrismaService();

function hashPassword(password: string): string {
  return createHash('sha256').update(password).digest('hex');
}

function generateReceiptNumber(tenantCode: string): string {
  const now = new Date();
  const yyyymmdd = `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, '0')}${String(now.getUTCDate()).padStart(2, '0')}`;
  const code = tenantCode.replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
  const randomSuffix = randomBytes(4).toString('hex').toUpperCase();
  return `RCP-${code}-${yyyymmdd}-${randomSuffix}`;
}

async function main() {
  console.log('🌱 Début du seeding...\n');

  // ──────────────────────────────────────
  // 1. Création du Tenant (École)
  // ──────────────────────────────────────
  const tenant = await prisma.tenant.upsert({
    where: { code: 'DEMO001' },
    update: {},
    create: {
      code: 'DEMO001',
      name: 'Lycée de Démonstration',
      currency: 'XAF',
      timezone: 'Africa/Ndjamena',
    },
  });
  console.log(`✅ École créée : ${tenant.name} (${tenant.code})`);

  // Compte virtuel (float numérique retirable) de l'école
  await prisma.virtualAccount.upsert({
    where: { tenantId: tenant.id },
    update: {},
    create: { tenantId: tenant.id, currency: tenant.currency },
  });
  console.log('✅ Compte virtuel initialisé (solde 0)');

  // ──────────────────────────────────────
  // 2. Création des Utilisateurs
  // ──────────────────────────────────────
  const adminUser = await prisma.user.upsert({
    where: { tenantId_email: { tenantId: tenant.id, email: 'admin@demo.td' } },
    update: {},
    create: {
      tenantId: tenant.id,
      email: 'admin@demo.td',
      fullName: 'Admin Démo',
      passwordHash: hashPassword('admin123'),
      role: 'TENANT_ADMIN',
      phone: '+23599112233',
    },
  });
  console.log(`✅ Admin créé : ${adminUser.fullName} (admin@demo.td / admin123)`);

  const cashierUser = await prisma.user.upsert({
    where: { tenantId_email: { tenantId: tenant.id, email: 'caissier@demo.td' } },
    update: {},
    create: {
      tenantId: tenant.id,
      email: 'caissier@demo.td',
      fullName: 'Caissier Démo',
      passwordHash: hashPassword('caissier123'),
      role: 'CASHIER',
      phone: '+23599112244',
    },
  });
  console.log(`✅ Caissier créé : ${cashierUser.fullName} (caissier@demo.td / caissier123)`);

  const accountantUser = await prisma.user.upsert({
    where: { tenantId_email: { tenantId: tenant.id, email: 'comptable@demo.td' } },
    update: {},
    create: {
      tenantId: tenant.id,
      email: 'comptable@demo.td',
      fullName: 'Comptable Démo',
      passwordHash: hashPassword('compta123'),
      role: 'ACCOUNTANT',
      phone: '+23599112255',
    },
  });
  console.log(`✅ Comptable créé : ${accountantUser.fullName} (comptable@demo.td / compta123)`);

  // ──────────────────────────────────────
  // 3. Création des Classes
  // ──────────────────────────────────────
  const classesData = [
    { name: '6ème A', academicYear: '2024-2025' },
    { name: '6ème B', academicYear: '2024-2025' },
    { name: '5ème A', academicYear: '2024-2025' },
    { name: '5ème B', academicYear: '2024-2025' },
    { name: '4ème A', academicYear: '2024-2025' },
    { name: '3ème A', academicYear: '2024-2025' },
  ];

  const classes = [];
  for (const cls of classesData) {
    const created = await prisma.schoolClass.upsert({
      where: { tenantId_name_academicYear: { tenantId: tenant.id, ...cls } },
      update: {},
      create: { tenantId: tenant.id, ...cls },
    });
    classes.push(created);
  }
  console.log(`✅ ${classes.length} classes créées`);

  // ──────────────────────────────────────
  // 4. Création des Étudiants
  // ──────────────────────────────────────
  const studentsData = [
    { studentNumber: 'ETU001', fullName: 'Jean Dupont', guardianPhone: '+23599110001', guardianName: 'Pierre Dupont', classIdx: 0, debtCents: 50000n },
    { studentNumber: 'ETU002', fullName: 'Marie Diallo', guardianPhone: '+23599110002', guardianName: 'Fatima Diallo', classIdx: 0, debtCents: 0n },
    { studentNumber: 'ETU003', fullName: 'Paul Kamga', guardianPhone: '+23599110003', guardianName: 'Joseph Kamga', classIdx: 1, debtCents: 75000n },
    { studentNumber: 'ETU004', fullName: 'Sarah Ngo', guardianPhone: '+23599110004', guardianName: 'David Ngo', classIdx: 1, debtCents: 30000n },
    { studentNumber: 'ETU005', fullName: 'Ahmed Mahamat', guardianPhone: '+23599110005', guardianName: 'Hassan Mahamat', classIdx: 2, debtCents: 25000n },
    { studentNumber: 'ETU006', fullName: 'Esther Tchoua', guardianPhone: '+23599110006', guardianName: 'Marc Tchoua', classIdx: 2, debtCents: 0n },
    { studentNumber: 'ETU007', fullName: 'Luc Mbarga', guardianPhone: '+23599110007', guardianName: 'Elise Mbarga', classIdx: 3, debtCents: 60000n },
    { studentNumber: 'ETU008', fullName: 'Grace Ndjock', guardianPhone: '+23599110008', guardianName: 'Thomas Ndjock', classIdx: 3, debtCents: 15000n },
    { studentNumber: 'ETU009', fullName: "Simon Oumar", guardianPhone: '+23599110009', guardianName: 'Amina Oumar', classIdx: 4, debtCents: 45000n },
    { studentNumber: 'ETU010', fullName: 'Rachel Bello', guardianPhone: '+23599110010', guardianName: 'Jean Bello', classIdx: 4, debtCents: 0n },
    { studentNumber: 'ETU011', fullName: 'David Fotso', guardianPhone: '+23599110011', guardianName: 'Christine Fotso', classIdx: 5, debtCents: 80000n },
    { studentNumber: 'ETU012', fullName: 'Sophie Mbah', guardianPhone: '+23599110012', guardianName: 'Paul Mbah', classIdx: 5, debtCents: 35000n },
  ];

  const students = [];
  for (const s of studentsData) {
    const created = await prisma.student.upsert({
      where: { tenantId_studentNumber: { tenantId: tenant.id, studentNumber: s.studentNumber } },
      update: {},
      create: {
        tenantId: tenant.id,
        studentNumber: s.studentNumber,
        fullName: s.fullName,
        guardianPhone: s.guardianPhone,
        guardianName: s.guardianName,
        classId: classes[s.classIdx].id,
        debtCents: s.debtCents,
      },
    });
    students.push(created);
  }
  console.log(`✅ ${students.length} étudiants créés`);

  // ──────────────────────────────────────
  // 5. Création des Transactions (paiements)
  // ──────────────────────────────────────
  const paymentsData = [
    { studentIdx: 1, amountCents: 50000n, note: 'Paiement 1er trimestre' },
    { studentIdx: 3, amountCents: 25000n, note: 'Paiement partiel' },
    { studentIdx: 5, amountCents: 75000n, note: 'Paiement intégral' },
    { studentIdx: 7, amountCents: 30000n, note: 'Paiement 1er versement' },
    { studentIdx: 9, amountCents: 100000n, note: 'Paiement annuel' },
    { studentIdx: 1, amountCents: 25000n, note: '2ème versement' },
    { studentIdx: 3, amountCents: 5000n, note: 'Acompte' },
    { studentIdx: 7, amountCents: 15000n, note: '2ème versement' },
  ];

  let txCount = 0;
  for (const p of paymentsData) {
    const student = students[p.studentIdx];
    const previousDebtCents = student.debtCents;
    let newDebtCents = previousDebtCents - p.amountCents;
    if (newDebtCents < 0n) newDebtCents = 0n;

    const daysAgo = Math.floor(Math.random() * 30);
    const recordedAt = new Date(Date.now() - daysAgo * 24 * 60 * 60 * 1000);

    // Update student debt
    await prisma.student.update({
      where: { id: student.id },
      data: {
        debtCents: newDebtCents,
        lastPaymentAt: recordedAt,
      },
    });

    const receiptNumber = generateReceiptNumber(tenant.code);
    const hashInput = [tenant.id, student.id, adminUser.id, p.amountCents.toString(), previousDebtCents.toString(), newDebtCents.toString(), 'XAF', receiptNumber, recordedAt.toISOString()].join('|');
    const receiptHash = createHash('sha256').update(hashInput).digest('hex');

    await prisma.transaction.create({
      data: {
        tenantId: tenant.id,
        studentId: student.id,
        recordedByUserId: adminUser.id,
        type: 'CASH_PAYMENT',
        status: 'POSTED',
        amountCents: p.amountCents,
        previousDebtCents,
        newDebtCents,
        currency: 'XAF',
        receiptNumber,
        receiptHash,
        note: p.note,
        recordedAt,
      },
    });
    txCount++;
  }
  console.log(`✅ ${txCount} transactions de démo créées`);

  // ──────────────────────────────────────
  // Résumé
  // ──────────────────────────────────────
  console.log('\n═══════════════════════════════════════');
  console.log('🎉 Seeding terminé avec succès !');
  console.log('═══════════════════════════════════════');
  console.log('\n📧 Comptes de démonstration :');
  console.log('   Admin    : admin@demo.td / admin123');
  console.log('   Caissier : caissier@demo.td / caissier123');
  console.log('   Comptable: comptable@demo.td / compta123');
  console.log('\n🏫 École : Lycée de Démonstration (Code: DEMO001)');
  console.log(`📚 ${classes.length} classes`);
  console.log(`👨‍🎓 ${students.length} étudiants`);
  console.log(`💳 ${txCount} transactions\n`);
}

main()
  .catch((e) => {
    console.error('❌ Erreur lors du seeding :', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });

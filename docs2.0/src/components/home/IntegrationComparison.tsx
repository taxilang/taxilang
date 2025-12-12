import * as React from 'react'
import { BigText, SectionHeading, SectionHeadingParagraph } from '@/components/common'
import { Snippet } from '@/components/Steps'
import PlaygroundSnippet from '@/components/PlaygroundSnippet'

interface IntegrationComparisonProps {
  highlightedJsCode: string;
}

const javascriptCodeSnippet = {
  name: 'integration.js',
  lang: 'javascript',
  code: `// Without Taxi: Integration c
async function getOrdersWithDetails(customerId) {
  const customer = await customerApi.getCustomer(customerId);
  const orders = await orderApi.getOrdersByCustomer(customerId);

  const enrichedOrders = await Promise.all(
    orders.map(async (order) => {
      const [shipping, payment] = await Promise.all([
        shippingApi.getTracking(order.trackingId).catch(() => null),
        paymentApi.getPayment(order.paymentId).catch(() => null),
      ]);

      return {
        orderId: order.id,
        total: order.total,
        customerName: customer.name,
        shippingStatus: shipping?.status ?? 'unknown',
        paymentMethod: payment?.method,
      };
    })
  );

  return enrichedOrders;
}`,
}

const taxiScenario = {
  schema: `
closed model Order {
   id : OrderId inherits String
   orderTotal: OrderTotal inherits Decimal
   customerId: CustomerId inherits String
}

closed model Customer {
   id : CustomerId
   name : CustomerName inherits String
}

enum PaymentMethod {
   Cash,
   Card,
   Account
}

closed model PaymentInfo {
   method : PaymentMethod
}

enum ShippingStatus {
   Packaging,
   Dispatched,
   Delivered
}

closed model ShippingInfo {
   status: ShippingStatus
}

service CustomerApi {
   operation findCustomer(CustomerId):Customer
}

service OrderApi {
   operation findOrders(CustomerId):Order[](...)
   operation getShippingStatus(OrderId):ShippingInfo
   operation getPaymentInfo(OrderId):PaymentInfo
}

`,
  query: `// With TaxiQL: Taxi automates the integration for you...
query getOrderWithDetails(customerId: CustomerId) {
  find { Order[](CustomerId == customerId) } as {
    orderId: OrderId
    total: OrderTotal
    customerName: CustomerName  // Taxi orchestres APIs to fetch this...
    shippingStatus: ShippingStatus // ...and this
    paymentMethod: PaymentMethod // ...and this
  }[]
}
`,
  parameters: {
    'customerId': '123',
  },
  'stubs': [
    {
      'operationName': 'findCustomer',
      'response': '{\n  "id" : "cust-123",\n  "name" : "Jimmy McJimmerson"\n}',
    },
    {
      'operationName': 'findOrders',
      'response': '[\n { "id" : "ord-123", "orderTotal" : 23.33, "customerId" : "cust-123" },\n { "id" : "ord-124", "orderTotal" : 13.11, "customerId" : "cust-123" }\n]',
    },
    {
      'operationName': 'getShippingStatus',
      'response': '',
      'conditionalResponses': [
        {
          'inputs': [
            {
              'name': 'p0',
              'value': 'ord-123',
            },
          ],
          'response': {
            'body': '{ "status" : "Packaging" }',
          },
        },
        {
          'inputs': [
            {
              'name': 'p0',
              'value': 'ord-124',
            },
          ],
          'response': {
            'body': '{ "status" : "Delivered" }',
          },
        },
      ],
    },
    {
      'operationName': 'getPaymentInfo',
      'response': '{ "method" : "Cash" }',
      'conditionalResponses': [
        {
          'inputs': [
            {
              'name': 'p0',
              'value': 'ord-123',
            },
          ],
          'response': {
            'body': '{ "method" : "Cash" }',
          },
        },
        {
          'inputs': [
            {
              'name': 'p0',
              'value': 'ord-124',
            },
          ],
          'response': {
            'body': '{ "method" : "Card" }',
          },
        },
      ],
    },
  ],
  readme: '',
  layout: {
    showDiagram: true,
    showReadme: false,
    showQuery: true,
    showSchema: false,
  }
}



export const IntegrationComparison: React.FC<IntegrationComparisonProps> = ({ highlightedJsCode }) => {
  return (
    <div className="max-w-7xl mx-auto p-4 py-16">
      <div className="text-center text-lg mb-12">
        <BigText>Describe what you want. Taxi handles the rest.</BigText>
        <SectionHeadingParagraph>
          <p>
            Traditional API integration requires writing and maintaining complex orchestration code. With Taxi, you
            simply describe the data you need, and Taxi automatically discovers how to fetch it.
          </p>
        </SectionHeadingParagraph>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
        {/* Left side: JavaScript integration code */}
        <div className="flex flex-col">
          <h3 className="text-xl font-semibold text-slate-200 mb-2">
            Without Taxi
          </h3>
          <p className="mb-4 text-sm text-slate-400">
            Manual orchestration code that needs maintenance when APIs change
          </p>
          <Snippet
            code={javascriptCodeSnippet}
            highlightedCode={highlightedJsCode}
          />

        </div>

        {/* Right side: Taxi query */}
        <div className="flex flex-col">
          <h3 className="text-xl font-semibold text-slate-200 mb-2">
            With Taxi
          </h3>
          <p className="mb-4 text-sm text-slate-400">
            Declarative query that adapts automatically as your APIs evolve
          </p>
          <PlaygroundSnippet
            title=""
            scenario={taxiScenario}
            primaryRunButton={true}
            showQueryPlan={false}
          >
            <PlaygroundSnippet.Description>
              Taxi automatically discovers data by joining across services.
              Click "Show Query Plan" to see how Taxi builds the integration.
            </PlaygroundSnippet.Description>
          </PlaygroundSnippet>
        </div>
      </div>
    </div>
  )
}
